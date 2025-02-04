package jp.co.sharp.workTogether.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;//追加日1/28
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;//追加日1/28
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toolbar;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;

import jp.co.sharp.android.voiceui.VoiceUIManager;
import jp.co.sharp.android.voiceui.VoiceUIVariable;
import jp.co.sharp.workTogether.app.voiceui.ScenarioDefinitions;
import jp.co.sharp.workTogether.app.voiceui.VoiceUIListenerImpl;
import jp.co.sharp.workTogether.app.voiceui.VoiceUIManagerUtil;
import jp.co.sharp.workTogether.app.voiceui.VoiceUIVariableUtil;
import jp.co.sharp.android.rb.projectormanager.ProjectorManagerServiceUtil;

/**
 * 音声UIを利用した最低限の機能だけ実装したActivity.
 */

public class ShowActivity extends Activity implements VoiceUIListenerImpl.ScenarioCallback {
    public static final String TAG = ShowActivity.class.getSimpleName();

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
    //private ShowActivity.ProjectorEventReceiver mProjectorEventReceiver;
    /**
     * プロジェクタ照射中のWakelock.
     */
    //private PowerManager.WakeLock mWakelock;
    /**
     * 排他制御用.
     */
    //private Object mLock = new Object();
    /**
     * プロジェクタ照射状態.
     */
    private boolean isProjecting = false;
    private Handler handler;//一定間隔で呼び出される発話スレッドの制御に使用
    private Runnable runnable;//一定間隔で呼び出される発話スレッドの制御に使用
    private boolean accostStopFrag;//一定間隔で呼び出される発話スレッドが停止しているかを表すフラグ(false:動作中 true:停止中)
    private String finalElapsedTime;//セッション中に経過した時間

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        setContentView(R.layout.show_activity);

        //ホームボタンの検知登録.
        mHomeEventReceiver = new HomeEventReceiver();
        IntentFilter filterHome = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mHomeEventReceiver, filterHome);

        // 終了ボタン取得
        Button finishButton = (Button) findViewById(R.id.finish_app_button);
        // 終了ボタンの処理
        finishButton.setOnClickListener(view -> {
            // Finish the current activity
            finish();
        });

        //過ぎた時間表示を更新
        TextView resultTimePassed = (TextView) findViewById(R.id.showActivity_output_text_value);
        finalElapsedTime = getElapsedTime(getIntentStringDataByKey("sessionStartTime"));
        if(Objects.equals(finalElapsedTime, "無効な時間形式")){
            finalElapsedTime = getIntentStringDataByKey("finalElapsedTimeLog");
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                resultTimePassed.setText(finalElapsedTime);
            }
        });

        //プロジェクター使用ボタン取得
        Button showProjectorButton = (Button) findViewById(R.id.show_result_button);
        //プロジェクター使用ボタンの処理
        showProjectorButton.setOnClickListener(view -> {
            //ShowDrawingActivityに移動
            startShowDrawing();
        });

        //落書の画像の配列を作成、その後、ランダムに選んで表示させる

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
        VoiceUIManagerUtil.enableScene(mVUIManager, ScenarioDefinitions.SCENE_SHOW);

        //アクティビティ起動時の発話
        String cF = getIntentStringDataByKey("checkFirst");
        if(Objects.equals(cF, "first")) {
            Log.v(TAG, "show.accosts.t1 Accosted");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SHOW_ACCOSTS + ".t1");//showActivityの初回起動時シナリオを起動する
        }else if(Objects.equals(cF, "not")){
            Log.v(TAG, "show.accosts.t3 Accosted");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SHOW_ACCOSTS + ".t3");//showActivityの二回目以降起動時シナリオを起動する
        }else{
            Log.v(TAG, "cannot get intentData");
        }

        //アクティビティ起動後一定時間ごとに発話
        accostStopFrag = false;//一定間隔で呼び出される発話スレッドが停止しているかを表すフラグ(false:動作中 true:停止中)
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                // UIスレッド
                if(!accostStopFrag) {
                    Log.v(TAG, "show.accost.t2 Accosted");
                    VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SHOW_ACCOSTS + ".t2");
                    handler.postDelayed(this, 15000);
                }
            }
        };
        handler.postDelayed(runnable, 15000);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "onPause()");

        //バックに回ったら発話を中止する.
        VoiceUIManagerUtil.stopSpeech();

        //Scene無効化.
        VoiceUIManagerUtil.disableScene(mVUIManager, ScenarioDefinitions.SCENE_COMMON);
        VoiceUIManagerUtil.disableScene(mVUIManager, ScenarioDefinitions.SCENE_SHOW);

        //VoiceUIListenerの解除.
        VoiceUIManagerUtil.unregisterVoiceUIListener(mVUIManager, mVUIListener);

        accostStopFrag = true;//一定間隔で呼び出される発話スレッドが停止しているかを表すフラグ(false:動作中 true:停止中)
        handler.removeCallbacks(runnable);//一定間隔で呼び出される発話スレッドの呼び出し予約を破棄する

        //プロジェクターが終わっても戻ってこないので終了する
        //finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy()");

        //ホームボタンの検知破棄.
        this.unregisterReceiver(mHomeEventReceiver);

        //インスタンスのごみ掃除.
        mVUIManager = null;
        mVUIListener = null;
    }

    /**
     * VoiceUIListenerクラスからのコールバックを実装する.
     */
    @Override
    public void onScenarioEvent(int event, List<VoiceUIVariable> variables) {
        Log.v(TAG, "onScenarioEvent() : " + event);
        switch (event) {
            case VoiceUIListenerImpl.ACTION_END:
                String function = VoiceUIVariableUtil.getVariableData(variables, ScenarioDefinitions.ATTR_FUNCTION);//ここで関数名を格納し、以下のif文で何の関数が呼ばれているのか判定する
                if(ScenarioDefinitions.FUNC_USE_PROJECTOR.equals(function)){//show_projectorシナリオのshow_projector関数
                    Log.v(TAG, "Receive Projector Voice Command heard");
                    //ShowDrawingActivityに移動
                    startShowDrawing();
                }
                if(ScenarioDefinitions.FUNC_END_APP.equals(function)){//show_endシナリオのshow_end関数
                    Log.v(TAG, "Receive End Voice Command heard");
                    finish();//アプリを終了する
                }
                break;
            case VoiceUIListenerImpl.RESOLVE_VARIABLE:
            case VoiceUIListenerImpl.ACTION_START:
            case VoiceUIListenerImpl.ACTION_CANCELLED:
            case VoiceUIListenerImpl.ACTION_REJECTED:
            default:
                break;
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
     * 指定された開始時刻 (HH:mm:ss) から現在時刻までの経過時間を計算する
     *
     * @param startTime 開始時刻の文字列 (フォーマット: HH:mm:ss)
     * @return 経過時間の文字列 (フォーマット: HH:mm:ss)、または無効な場合は "無効な時間形式"
     */
    private static String getElapsedTime(String startTime) {
        try {
            // 現在の時刻を取得 (Calendar → Date)
            Calendar calendar = Calendar.getInstance();
            Date now = calendar.getTime();

            // SimpleDateFormat を使用して文字列を Date に変換
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
            Date start = timeFormat.parse(startTime);

            // 経過時間（ミリ秒単位）を計算
            long elapsedMillis = now.getTime() - start.getTime();

            // 経過時間が負の場合は、絶対値を取る（未来時間の考慮）
            if (elapsedMillis < 0) {
                elapsedMillis = Math.abs(elapsedMillis);
            }

            // ミリ秒を時間、分、秒に変換
            long hours = (elapsedMillis / (1000 * 60 * 60)) % 24; // 24時間を超えないように
            long minutes = (elapsedMillis / (1000 * 60)) % 60;
            long seconds = (elapsedMillis / 1000) % 60;

            // hh:mm:ss 形式の文字列を作成
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } catch (ParseException e) {
            return "無効な時間形式"; // 入力が不正な場合のエラーハンドリング
        }
    }

<<<<<<< Updated upstream
<<<<<<< Updated upstream
    private void startShowDrawing() {//TASK showDrawingにelapsedtimeの情報を通してまた戻して、とする必要がある
=======
    public void startShowDrawing() {//TASK showDrawingにelapsedtimeの情報を通してまた戻して、とする必要がある
>>>>>>> Stashed changes
=======
    public void startShowDrawing() {//TASK showDrawingにelapsedtimeの情報を通してまた戻して、とする必要がある
>>>>>>> Stashed changes
        startProjector();
        Bundle extras = new Bundle();
        extras.putString("finalElapsedTimeLog",finalElapsedTime);
        navigateToActivity(this, ShowDrawingActivity.class, extras);//ShowActivityを呼び出す

        finish();//ShowDrawingActivityを呼んだらすぐに終了する
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

<<<<<<< Updated upstream
<<<<<<< Updated upstream
    private void startProjector(){
=======
    public void startProjector(){
>>>>>>> Stashed changes
=======
    public void startProjector(){
>>>>>>> Stashed changes
        if(!isProjecting) {//すでにプロジェクターが起動中でなければ
            Log.v(TAG, "Try Start Projector");
            //プロジェクター起動
            startService(getIntentForProjector());
            isProjecting = true;
        }else{
            Log.v(TAG, "Try Start Projector,But Projector Is Starting now");
        }
    }

//    public void stopProjector() {
//        if(isProjected) {//すでにプロジェクターが起動済みなら
//            Log.v(TAG, "Try Stop Projector");
//            //プロジェクター終了
//            stopService(getIntentForProjector());
//        }else{
//            Log.v(TAG, "Try Stop Projector,But Projector Have Not Started Yet");
//        }
//
//    }

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
//    private void setProjectorEventReceiver() {
//        Log.v(TAG, "setProjectorEventReceiver()");
//        if (mProjectorEventReceiver == null) {
//            mProjectorEventReceiver = new ShowActivity.ProjectorEventReceiver();
//        } else {
//            return;
//        }
//        IntentFilter intentFilter = new IntentFilter();
//        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_PREPARE);
//        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_START);
//        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_PAUSE);
//        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_RESUME);
//        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_END);
//        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_END_ERROR);
//        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_END_FATAL_ERROR);
//        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_TERMINATE);
//        registerReceiver(mProjectorEventReceiver, intentFilter);
//    }
//
//    /**
//     * WakeLockを取得する.
//     */
//    private void acquireWakeLock() {
//        Log.v(TAG, "acquireWakeLock()");
//        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
//        synchronized (mLock) {
//            if (mWakelock == null || !mWakelock.isHeld()) {
//                mWakelock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
//                        | PowerManager.ACQUIRE_CAUSES_WAKEUP
//                        | PowerManager.ON_AFTER_RELEASE, MainActivity.class.getName());
//                mWakelock.acquire();
//            }
//        }
//    }
//
//    /**
//     * WakeLockを開放する.
//     */
//    private void releaseWakeLock() {
//        Log.v(TAG, "releaseWakeLock()");
//        synchronized (mLock) {
//            if (mWakelock != null && mWakelock.isHeld()) {
//                mWakelock.release();
//                mWakelock = null;
//            }
//        }
//    }
//
//    /**
//     * プロジェクターの状態変化時のイベントを受け取るためのBroadcastレシーバークラス.<br>
//     * <p/>
//     * 照射開始時にはWakeLockの取得、終了時にはWakeLockの開放する.<br>
//     * アプリ仕様に応じて必要な処理があれば実装すること.
//     */
//    private class ProjectorEventReceiver extends BroadcastReceiver {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            Log.v(TAG, "ProjectorEventReceiver#onReceive():" + intent.getAction());
//            switch (intent.getAction()) {
//                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_PREPARE://プロジェクター照射準備通知
//                    Log.v(TAG, "プロジェクター照射準備通知");
//                    break;
//                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_PAUSE://プロジェクター照射一時停止通知
//                    Log.v(TAG, "プロジェクター照射一時停止通知");
//                    break;
//                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_RESUME://プロジェクター照射再開通知
//                    Log.v(TAG, "プロジェクター照射再開通知");
//                    break;
//                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_START://プロジェクター照射開始通知
//                    acquireWakeLock();
//                    Log.v(TAG, "プロジェクター照射開始通知");
//                    isProjected = true;
//                    break;
//                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_TERMINATE://プロジェクター終了処理開始通知
//                    Log.v(TAG, "プロジェクター終了処理開始通知");
//                    break;
//                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_END://プロジェクター終了通知
//                    Log.v(TAG, "プロジェクター終了処理通知");
//                    releaseWakeLock();
//                    isProjected = false;
//                    //endShowDrawing();
//                    break;
//                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_END_ERROR://プロジェクター異常終了通知
//                    Log.v(TAG, "プロジェクター異常終了通知");
//                    break;
//                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_END_FATAL_ERROR://プロジェクター異常終了通知（復旧不可能）
//                    Log.v(TAG, "プロジェクター異常終了通知(復旧不可能)");
//                    break;
//                default:
//                    break;
//            }
//        }
//    }


}