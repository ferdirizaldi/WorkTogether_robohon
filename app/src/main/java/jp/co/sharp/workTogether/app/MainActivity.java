package jp.co.sharp.workTogether.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toolbar;

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


/**
 * 音声UIを利用した最低限の機能だけ実装したActivity.
 */

public class MainActivity extends Activity implements VoiceUIListenerImpl.ScenarioCallback {
    public static final String TAG = MainActivity.class.getSimpleName();

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
    private Handler handler;//一定間隔で呼び出される発話スレッドの制御に使用
    private Runnable runnable;//一定間隔で呼び出される発話スレッドの制御に使用
    private boolean accostStopFrag;//一定間隔で呼び出される発話スレッドが停止しているかを表すフラグ(false:動作中 true:停止中)


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        setContentView(R.layout.main_activity);

        //タイトルバー設定.
        //setupTitleBar();

        //ホームボタンの検知登録.
        mHomeEventReceiver = new HomeEventReceiver();
        IntentFilter filterHome = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mHomeEventReceiver, filterHome);

        // UI表示
        initializeMainUI();

    }

    /**
     * Initializes buttons and their click listeners.
     */
    private void initializeMainUI() {
        // Button references
//        Button oneHour_button = (Button) findViewById(R.id.oneHour_button);
//        Button twoHours_button = (Button) findViewById(R.id.twoHours_button);
        Button noLimit_button = (Button) findViewById(R.id.noLimit_button);
        Button selectButton = (Button) findViewById(R.id.select_button);
        Button finishButton = (Button) findViewById(R.id.finish_app_button);
        NumberPicker numPicker = (NumberPicker)findViewById(R.id.numPicker);

//        // Set click listeners
//        oneHour_button.setOnClickListener(v -> {
//            startSession(1);//一時間のセッションを開始
//        });
//
//        twoHours_button.setOnClickListener(v -> {
//            startSession(2);//二時間のセッションを開始
//        });
        noLimit_button.setOnClickListener(v -> {
            startSession(0);//時間指定せずにセッションを開始
        });

        numPicker.setMaxValue(9);
        numPicker.setMinValue(1);

        // Set click listeners
        selectButton.setOnClickListener(v -> {
            startSession(numPicker.getValue());//1~9時間のセッションを開始
        });

        finishButton.setOnClickListener(v -> {
            // Finish the current activity
            finish();
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

        //Scene有効化
        VoiceUIManagerUtil.enableScene(mVUIManager, ScenarioDefinitions.SCENE_COMMON);
        VoiceUIManagerUtil.enableScene(mVUIManager, ScenarioDefinitions.SCENE_START);

        //アプリ起動時に発話
        Log.v(TAG, "start.accosts.t1 Accosted");
        VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_START_ACCOSTS + ".t1");

        //アプリ起動後一定時間ごとに発話
        accostStopFrag = false;
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                // UIスレッド
                if(!accostStopFrag) {
                    Log.v(TAG, "start.accosts.t2 Accosted");
                    VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_START_ACCOSTS + ".t2");
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
        VoiceUIManagerUtil.disableScene(mVUIManager, ScenarioDefinitions.SCENE_START);

        //VoiceUIListenerの解除.
        VoiceUIManagerUtil.unregisterVoiceUIListener(mVUIManager, mVUIListener);

        accostStopFrag = true;//一定間隔で呼び出される発話スレッドが停止しているかを表すフラグ(false:動作中 true:停止中)
        handler.removeCallbacks(runnable);//一定間隔で呼び出される発話スレッドの呼び出し予約を破棄する

        //単一Activityの場合はonPauseでアプリを終了する.
        finish();
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
                if(ScenarioDefinitions.FUNC_SEND_LENGTH.equals(function)){
                    //シナリオから送られてきたSession_LengthをStringからIntへ変換し、その時間のセッションを開始する
                    startSession(Integer.parseInt(VoiceUIVariableUtil.getVariableData(variables, ScenarioDefinitions.KEY_SESSION_LENGTH)));
                }

                if(ScenarioDefinitions.FUNC_END_APP.equals(function)){//start_endシナリオのend_app関数
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

    public void startSession(int sessionLength){
        if(sessionLength != 0) {
            VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_SESSION_LENGTH, sessionLength + "時間");
        }

        Bundle extras = new Bundle();
        extras.putInt("sessionLength", sessionLength);
        navigateToActivity(MainActivity.this, SessionActivity.class, extras);
        finish();
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
     * タイトルバーを設定する.
     */
//    private void setupTitleBar() {
//        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
//        setActionBar(toolbar);
//    }

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

}