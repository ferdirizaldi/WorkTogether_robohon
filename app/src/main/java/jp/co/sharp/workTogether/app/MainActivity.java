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
import android.view.View;//追加1/17 multilingualからのコピペ
import android.view.WindowManager;
import android.widget.Button;//追加1/17 multilingualからのコピペ
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toolbar;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import jp.co.sharp.android.voiceui.VoiceUIManager;
import jp.co.sharp.android.voiceui.VoiceUIVariable;
import jp.co.sharp.workTogether.app.voiceui.ScenarioDefinitions;
import jp.co.sharp.workTogether.app.voiceui.VoiceUIListenerImpl;
import jp.co.sharp.workTogether.app.voiceui.VoiceUIManagerUtil;
import jp.co.sharp.workTogether.app.voiceui.VoiceUIVariableUtil;//追加1/17 multilingualからのコピペ


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
    private String sessionTime = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        setContentView(R.layout.main_activity);

        //タイトルバー設定.
        setupTitleBar();

        //ホームボタンの検知登録.
        mHomeEventReceiver = new HomeEventReceiver();
        IntentFilter filterHome = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mHomeEventReceiver, filterHome);

        // Prevent the keyboard from showing up on app start
        //getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // UI表示
        initializeMainUI();

    }

    /**
     * Initializes buttons and their click listeners.
     */
    private void initializeMainUI() {
        // Button references
        Button oneHour_button = (Button) findViewById(R.id.oneHour_button);
        Button twoHours_button = (Button) findViewById(R.id.twoHours_button);
        Button noLimit_button = (Button) findViewById(R.id.noLimit_button);
        Button finishButton = (Button) findViewById(R.id.finish_app_button);

        // Set click listeners
        oneHour_button.setOnClickListener(v -> {
            sessionTime = "1時間";
            VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_SESSION_TIME, sessionTime);
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_START_END_SPEAK);
            // Delay navigation for a set time (e.g., 2 seconds)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Bundle extras = new Bundle();
                extras.putString("SessionName", "1Hour");
                extras.putInt("SessionLong", 1);
                navigateToActivity(MainActivity.this, SessionActivity.class, extras);
                Log.v(TAG, "Delayed navigation to SessionActivity after speech.");
                finish();
            }, 4000); // 2000ms delay
        });

        twoHours_button.setOnClickListener(v -> {
            sessionTime = "2時間";
            VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_SESSION_TIME, sessionTime);
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_START_END_SPEAK);
            // Delay navigation for a set time (e.g., 2 seconds)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Bundle extras = new Bundle();
                extras.putString("SessionName", "1Hour");
                extras.putInt("SessionLong", 1);
                navigateToActivity(MainActivity.this, SessionActivity.class, extras);
                Log.v(TAG, "Delayed navigation to SessionActivity after speech.");
                finish();
            }, 4000); // 2000ms delay
        });

        noLimit_button.setOnClickListener(v -> {
            sessionTime = "無限";
            VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_SESSION_TIME, sessionTime);
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_START_END_SPEAK);
            // Delay navigation for a set time (e.g., 2 seconds)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Bundle extras = new Bundle();
                extras.putString("SessionName", "無限");
                extras.putInt("SessionLong", -1);
                navigateToActivity(MainActivity.this, SessionActivity.class, extras);
                Log.v(TAG, "Delayed navigation to SessionActivity after speech.");
                finish();
            }, 4000); // 2000ms delay
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
        VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_HELLO);

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
                if(ScenarioDefinitions.FUNC_SEND_WORD.equals(function)) {//listenシナリオのsend_word関数
                    final String sessionTime = VoiceUIVariableUtil.getVariableData(variables, ScenarioDefinitions.KEY_LVCSR_BASIC);//聞いた単語をString変数に格納

                    //移動前にセッション開始の発話
                    VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_SESSION_TIME, sessionTime);
                    VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_START_END_SPEAK);

                    if(!(Objects.equals(sessionTime, ""))) {//正常なテキストなら一連の処理を開始する
                        Log.v(TAG, "Listen Scenario Sent Normal Text");

                        if(sessionTime != null && sessionTime.contains("1")){
                            // Delay navigation for a set time (e.g., 2 seconds)
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                Bundle extras = new Bundle();
                                extras.putString("SessionName", "1Hour");
                                extras.putInt("SessionLong", 1);
                                navigateToActivity(MainActivity.this, SessionActivity.class, extras);
                                Log.v(TAG, "Delayed navigation to SessionActivity after speech.");
                                finish();
                            }, 4000); // 2000ms delay
                            finish();//アプリを終了する
                        }
                        else if(sessionTime != null && sessionTime.contains("2")){
                            // Delay navigation for a set time (e.g., 2 seconds)
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                Bundle extras = new Bundle();
                                extras.putString("SessionName", "2Hours");
                                extras.putInt("SessionLong", 2);
                                navigateToActivity(MainActivity.this, SessionActivity.class, extras);
                                Log.v(TAG, "Delayed navigation to SessionActivity after speech.");
                                finish();
                            }, 4000); // 2000ms delay
                            finish();//アプリを終了する
                        }
                        else{
                            // Delay navigation for a set time (e.g., 2 seconds)
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                Bundle extras = new Bundle();
                                extras.putString("SessionName", "無限");
                                extras.putInt("SessionLong", -1);
                                navigateToActivity(MainActivity.this, SessionActivity.class, extras);
                                Log.v(TAG, "Delayed navigation to SessionActivity after speech.");
                                finish();
                            }, 4000); // 2000ms delay
                            finish();//アプリを終了する
                        }


                    }else{
                        Log.v(TAG, "Listen Scenario Sent Empty Text");
                    }
                }


                if(ScenarioDefinitions.FUNC_END_APP.equals(function)){//endシナリオのend_app関数
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
     * 翻訳をしてspeakシナリオを開始させる関数
     */
//    private void startSpeakScenario(final String original_word){
//        if(speak_flag == 1){
//            Log.v(TAG, "Speak Scenario Is During Execution");
//            return;//すでにspeakシナリオが実行中の場合はリターン
//        }
//        if(Objects.equals(original_word,null) || original_word.length() > max_length){
//            Log.v(TAG, "Original_word Is Wrong");
//            speak_again_flag = 0;//不具合時はspeak_againフラグを下げる
//            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_TRANSLATE);//errorシナリオのtranslateトピックを起動する
//            return;//original_wordが不正な場合はリターン
//        }
//
//        final String translated_word = translateSync(original_word);//original_wordを英訳したtranslated_wordを作成する
//        if(translated_word.contains("Error during translation")){
//            Log.v(TAG, "Translated_word Is Error Message");
//            speak_again_flag = 0;//不具合時はspeak_againフラグを下げる
//            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_CONNECTION);//errorシナリオのconnectionトピックを起動する
//            return;//translated_wordがエラーメッセージなのでリターン
//        }
//        if(Objects.equals(translated_word, null) || translated_word.length() > max_length){
//            Log.v(TAG, "Translated_word Is Wrong");
//            speak_again_flag = 0;//不具合時はspeak_againフラグを下げる
//            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_TRANSLATE);//errorシナリオのtranslateトピックを起動する
//            return;//translated_wordが不正な場合はリターン
//        }
//
//        int result = VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_ORIGINAL_WORD, original_word);//翻訳前の単語をspeakシナリオの手が届くpメモリに送る
//        if(Objects.equals(result,VoiceUIManager.VOICEUI_ERROR)){
//            Log.v(TAG, "Set Original_word Failed");
//            speak_again_flag = 0;//不具合時はspeak_againフラグを下げる
//            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_TRANSLATE);//errorシナリオのtranslateトピックを起動する
//            return;//original_wordのpメモリへの保存が失敗したらリターン
//        }
//        result = VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_TRANSLATED_WORD, translated_word);//翻訳後の単語をspeakシナリオの手が届くpメモリに送る
//        if(Objects.equals(result,VoiceUIManager.VOICEUI_ERROR)){
//            Log.v(TAG, "Set Translated_word Failed");
//            speak_again_flag = 0;//不具合時はspeak_againフラグを下げる
//            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_TRANSLATE);//errorシナリオのtranslateトピックを起動する
//            return;//translated_wordのpメモリへの保存が失敗したらリターン
//        }
//
//        result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SPEAK);//speakシナリオを起動する
//        if(Objects.equals(result,VoiceUIManager.VOICEUI_ERROR)){
//            Log.v(TAG, "Speak Scenario Failed To Start");
//            speak_again_flag = 0;//不具合時はspeak_againフラグを下げる
//            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_TRANSLATE);//errorシナリオのtranslateトピックを起動する
//        }else{
//            speak_flag = 1;//speakシナリオが正常に開始したらフラグを立てる
//            speak_again_flag = 1;
//            Log.v(TAG, "Speak Scenario Started");
//
//        }
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

    /**
     * タイトルバーを設定する.
     */
    private void setupTitleBar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);
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

}