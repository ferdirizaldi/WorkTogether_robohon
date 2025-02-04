package jp.co.sharp.workTogether.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import jp.co.sharp.android.rb.projectormanager.ProjectorManagerServiceUtil;
import jp.co.sharp.android.voiceui.VoiceUIManager;
import jp.co.sharp.android.voiceui.VoiceUIVariable;
import jp.co.sharp.workTogether.app.voiceui.ScenarioDefinitions;
import jp.co.sharp.workTogether.app.voiceui.VoiceUIListenerImpl;
import jp.co.sharp.workTogether.app.voiceui.VoiceUIManagerUtil;
import jp.co.sharp.workTogether.app.voiceui.VoiceUIVariableUtil;

/**
 * 音声UIを利用した最低限の機能だけ実装したActivity.
 */

public class ShowDrawingActivity extends Activity implements VoiceUIListenerImpl.ScenarioCallback {
    public static final String TAG = ShowDrawingActivity.class.getSimpleName();

    /**
     * 音声UI制御.
     */
    private VoiceUIManager mVUIManager = null;
    /**
     * 音声UIイベントリスナー.
     */
    private VoiceUIListenerImpl mVUIListener = null;
    /**
     * ホームボタンイベント検知.
     */
    private HomeEventReceiver mHomeEventReceiver;
    /**
     * プロジェクター状態変化イベント検知.
     */
    private ProjectorEventReceiver mProjectorEventReceiver;
    /**
     * プロジェクタ照射中のWakelock.
     */
    private PowerManager.WakeLock mWakelock;
    /**
     * 排他制御用.
     */
    private Object mLock = new Object();
    /**
     * プロジェクタ照射状態.
     */
    private boolean isProjected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        setContentView(R.layout.show_drawing);

        //ホームボタンの検知登録.
        mHomeEventReceiver = new HomeEventReceiver();
        IntentFilter filterHome = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mHomeEventReceiver, filterHome);

        //プロジェクタイベントの検知登録.
        setProjectorEventReceiver();

        // 終了ボタン取得
        Button finishButton = (Button) findViewById(R.id.finish_app_button);
        // 終了ボタンの処理
        finishButton.setOnClickListener(view -> {
            // Finish the current activity

            // Projector also needs to end button process
            stopProjector();
            //endShowDrawing();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.v(TAG, "onResume()");

        //VoiceUIManagerインスタンス生成.
        if(mVUIManager == null){
            mVUIManager = VoiceUIManager.getService(this);
        }
        //VoiceUIListenerインスタンス生成.
        if (mVUIListener == null) {
            mVUIListener = new VoiceUIListenerImpl(this);
        }
        //VoiceUIListenerの登録.
        VoiceUIManagerUtil.registerVoiceUIListener(mVUIManager, mVUIListener);

        //Scene有効化.
        VoiceUIManagerUtil.enableScene(mVUIManager, ScenarioDefinitions.SCENE_COMMON);
        //VoiceUIManagerUtil.enableScene(mVUIManager, ScenarioDefinitions.SCENE_SHOW);


        //Show Projector関数で画像表示
        //startProjector();

    }

    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "onPause()");

        //バックに回ったら発話を中止する.
        VoiceUIManagerUtil.stopSpeech();

        //Scene無効化.
        VoiceUIManagerUtil.disableScene(mVUIManager, ScenarioDefinitions.SCENE_COMMON);
        //VoiceUIManagerUtil.disableScene(mVUIManager, ScenarioDefinitions.SCENE_SHOW);

        //VoiceUIListenerの解除.
        VoiceUIManagerUtil.unregisterVoiceUIListener(mVUIManager, mVUIListener);

        //プロジェクター起動時にもonPauseは呼ばれるのでここで終了したらまずい
        //finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy()");

        //ホームボタンの検知破棄.
        this.unregisterReceiver(mHomeEventReceiver);

        //プロジェクタイベントの検知破棄.
        this.unregisterReceiver(mProjectorEventReceiver);

        //インスタンスのごみ掃除.
        mVUIManager = null;
        mVUIListener = null;
        mProjectorEventReceiver = null;
    }

    /**
     * VoiceUIListenerクラスからのコールバックを実装する.
     */
    @Override
    public void onScenarioEvent(int event, List<VoiceUIVariable> variables) {
        Log.v(TAG, "onScenarioEvent() : " + event);
        switch (event) {
            case VoiceUIListenerImpl.ACTION_END:
                String function = VoiceUIVariableUtil.getVariableData(variables, ScenarioDefinitions.ATTR_FUNCTION);
                break;
            case VoiceUIListenerImpl.RESOLVE_VARIABLE:
            case VoiceUIListenerImpl.ACTION_START:
            case VoiceUIListenerImpl.ACTION_CANCELLED:
            case VoiceUIListenerImpl.ACTION_REJECTED:
            default:
                break;
        }
    }

//    public void startProjector() {
//        //プロジェクターでできあがあった絵を見せる
//        if (!isProjected) {//すでにプロジェクターが起動済みでなければ
//            Log.v(TAG, "Try Start Projector");
//            //プロジェクター起動
//            startService(getIntentForProjector());
//        } else {
//            Log.v(TAG, "Try Start Projector,But Projector Is Already Started");
//        }
//    }

    /**
     * Stops the projector using ProjectorManagerServiceUtil.
     */
//    public void stopProjector() {
//        Log.v(TAG, "Stopping Projector");
//
//        // プロジェクター終了通知.
//        Intent notifyEndIntent = new Intent();
//        ComponentName componentName = new ComponentName(
//                ProjectorManagerServiceUtil.PACKAGE_NAME,
//                ProjectorManagerServiceUtil.CLASS_NAME);
//
//        notifyEndIntent.setComponent(componentName);
//        notifyEndIntent.setAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_END);
//
//        startService(notifyEndIntent);  //プロジェクター終了通知.
//
//        // プロジェクター終了処理開始通知.
//        Intent terminateIntent = new Intent();
//        terminateIntent.setComponent(componentName);
//        terminateIntent.setAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_TERMINATE);
//
//        startService(terminateIntent);  // プロジェクター終了処理開始通知.
//    }

    private void stopProjector() {
        if(isProjected) {//すでにプロジェクターが起動済みなら
            Log.v(TAG, "Try Stop Projector");
            //プロジェクター終了
            stopService(getIntentForProjector());
        }else{
            Log.v(TAG, "Try Stop Projector,But Projector Have Not Started Yet");
            //ありえないとは思うが、プロジェクタが素早く起動し、このアクティビティのレシーバーがセットされるより前に照射開始通知が出た場合、
            //それを受け取れずisProjectedがtrueになっていないのにプロジェクタが起動している状態になるかもしれない。
            //その場合でもこの関数(ボタンからのみ呼ばれる)を使わず音声コマンドで終了させることは可能
        }
    }


    /**
     * プロジェクターマネージャーの開始/停止用のIntentを設定する.
     */
    private Intent getIntentForProjector() {
        Intent intent = new Intent();
        ComponentName componentName = new ComponentName(
                ProjectorManagerServiceUtil.PACKAGE_NAME,
                ProjectorManagerServiceUtil.CLASS_NAME);
        //逆方向で照射する
        intent.putExtra(ProjectorManagerServiceUtil.EXTRA_PROJECTOR_OUTPUT, ProjectorManagerServiceUtil.EXTRA_PROJECTOR_OUTPUT_VAL_REVERSE);
        //足元に照射する
        intent.putExtra(ProjectorManagerServiceUtil.EXTRA_PROJECTOR_DIRECTION, ProjectorManagerServiceUtil.EXTRA_PROJECTOR_DIRECTION_VAL_UNDER);
        intent.setComponent(componentName);
        return intent;
    }

    /**
     * プロジェクターの状態変化イベントを受け取るためのレシーバーをセットする.
     */
    private void setProjectorEventReceiver() {
        Log.v(TAG, "setProjectorEventReceiver()");
        if (mProjectorEventReceiver == null) {
            mProjectorEventReceiver = new ProjectorEventReceiver();
        } else {
            return;
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_PREPARE);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_START);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_PAUSE);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_RESUME);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_END);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_END_ERROR);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_END_FATAL_ERROR);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_TERMINATE);
        registerReceiver(mProjectorEventReceiver, intentFilter);
    }

    /**
     * WakeLockを取得する.
     */
    private void acquireWakeLock() {
        Log.v(TAG, "acquireWakeLock()");
        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        synchronized (mLock) {
            if (mWakelock == null || !mWakelock.isHeld()) {
                mWakelock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP
                        | PowerManager.ON_AFTER_RELEASE, MainActivity.class.getName());
                mWakelock.acquire();
            }
        }
    }

    /**
     * WakeLockを開放する.
     */
    private void releaseWakeLock() {
        Log.v(TAG, "releaseWakeLock()");
        synchronized (mLock) {
            if (mWakelock != null && mWakelock.isHeld()) {
                mWakelock.release();
                mWakelock = null;
            }
        }
    }

    /**
     * プロジェクターの状態変化時のイベントを受け取るためのBroadcastレシーバークラス.<br>
     * <p/>
     * 照射開始時にはWakeLockの取得、終了時にはWakeLockの開放する.<br>
     * アプリ仕様に応じて必要な処理があれば実装すること.
     */
    private class ProjectorEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "ProjectorEventReceiver#onReceive():" + intent.getAction());
            switch (intent.getAction()) {
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_PREPARE://プロジェクター照射準備通知
                    Log.v(TAG, "プロジェクター照射準備通知");
                    break;
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_PAUSE://プロジェクター照射一時停止通知
                    Log.v(TAG, "プロジェクター照射一時停止通知");
                    break;
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_RESUME://プロジェクター照射再開通知
                    Log.v(TAG, "プロジェクター照射再開通知");
                    break;
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_START://プロジェクター照射開始通知
                    Log.v(TAG, "プロジェクター照射開始通知");
                    acquireWakeLock();
                    isProjected = true;
                    break;
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_TERMINATE://プロジェクター終了処理開始通知
                    Log.v(TAG, "プロジェクター終了処理開始通知");
                    break;
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_END://プロジェクター終了通知
                    Log.v(TAG, "プロジェクター終了処理通知");
                    releaseWakeLock();
                    isProjected = false;
                    endShowDrawing();
                    break;
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_END_ERROR://プロジェクター異常終了通知
                    Log.v(TAG, "プロジェクター異常終了通知");
                    releaseWakeLock();
                    isProjected = false;
                    endShowDrawing();
                    break;
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_END_FATAL_ERROR://プロジェクター異常終了通知（復旧不可能）
                    Log.v(TAG, "プロジェクター異常終了通知(復旧不可能)");
                    releaseWakeLock();
                    isProjected = false;
                    endShowDrawing();
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * ホームボタンの押下イベントを受け取るためのBroadcastレシーバークラス.<br>
     * <p/>
     * アプリは必ずホームボタンで終了する.
     */
    private class HomeEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "Receive Home button pressed");
            // ホームボタン押下でアプリ終了する.
            finish();
        }
    }

    public void endShowDrawing() {
        Bundle extras = new Bundle();
        extras.putString("checkFirst", "not");
        extras.putString("finalElapsedTimeLog", getIntentStringDataByKey("finalElapsedTimeLog"));
        navigateToActivity(this, ShowActivity.class, extras);//ShowActivityを呼び出す

        finish();//ShowActivityを呼んだらすぐに終了する
    }

    /**
     * Retrieves the extras from the intent and returns them
     *
     * @return A string data is the session data or error massage if no extras exist by key.
     * //IntentからのString型変数を受け取る関数
     */
    private String getIntentStringDataByKey(String key) {
        Intent intent = getIntent();
        if (intent != null) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                return extras.getString(key, "wrongKey");
            }
        }

        // Return it if no extras are found
        Log.v(TAG, "No Extras Are Found");
        return "no extras are found";
    }

    /**
     * Helper method for transitioning to another activity.
     *
     * @param context       Current context (usually `this` in an activity).
     * @param targetActivity Target activity class for the transition.
     * @param extras         Optional data to pass to the target activity (can be null).
     */
    /**
     //データ渡しなしのActivity移動
     navigateToActivity(this, TargetActivity.class, null);
     //データ渡しなしのActivity移動
     Bundle extras = new Bundle();
     extras.putString("key", "value");
     extras.putInt("another_key", 123);
     navigateToActivity(this, TargetActivity.class, extras);
     **/
    private void navigateToActivity(Context context, Class<?> targetActivity, Bundle extras) {
        Intent intent = new Intent(context, targetActivity);
        if (extras != null) {
            intent.putExtras(extras);
        }
        context.startActivity(intent);
    }

    /**
     * Stops the projector using ProjectorManagerServiceUtil.
     */
//    public void stopProjector() {
//        Log.v(TAG, "Stopping Projector");
//
//        // Step 1: Notify the projector to stop
//        Intent notifyEndIntent = new Intent(ProjectorManagerServiceUtil.ACTION_PROJECTOR_END);
//        sendBroadcast(notifyEndIntent);
//        Log.v(TAG, "Sent ACTION_PROJECTOR_END");
//
//        // Step 2: Wait before stopping the service
//        Intent stopIntent = new Intent();
//        ComponentName componentName = new ComponentName(
//                ProjectorManagerServiceUtil.PACKAGE_NAME,
//                ProjectorManagerServiceUtil.CLASS_NAME);
//
//        stopIntent.setComponent(componentName);
//
//        Log.v(TAG, "Attempting to stop projector service...");
//        boolean stopped = stopService(stopIntent);
//
//        if (stopped) {
//            Log.v(TAG, "Projector service stopped successfully.");
//            isProjected = false; // Update state to prevent restart
//        } else {
//            Log.e(TAG, "Failed to stop projector service.");
//        }
//    }

}