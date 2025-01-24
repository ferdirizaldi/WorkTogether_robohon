package jp.co.sharp.workTogether.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;//追加日1/24
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toolbar;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;//追加日1/24
import java.util.concurrent.ScheduledExecutorService;//追加日1/24
import java.util.concurrent.TimeUnit;//追加日1/24
import java.util.Timer;//追加日1/24
import java.util.TimerTask;//追加日1/24

import jp.co.sharp.android.voiceui.VoiceUIManager;
import jp.co.sharp.android.voiceui.VoiceUIVariable;
import jp.co.sharp.workTogether.app.voiceui.ScenarioDefinitions;
import jp.co.sharp.workTogether.app.voiceui.VoiceUIListenerImpl;
import jp.co.sharp.workTogether.app.voiceui.VoiceUIManagerUtil;
import jp.co.sharp.workTogether.app.voiceui.VoiceUIVariableUtil;

/**
 * 音声UIを利用した最低限の機能だけ実装したActivity.
 */

public class SessionActivity extends Activity implements VoiceUIListenerImpl.ScenarioCallback {
    public static final String TAG = SessionActivity.class.getSimpleName();

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

    final private int workTime = 60 * 25;//デフォルトの作業時間
    final private int workSnoozeTime = 60 * 5;//作業中止の提案の周期
    final private int workActionTime = 60 * 1;//作業中の動作の周期
    final private int breakTime = 60 * 5;//デフォルトの休憩時間
    final private int breakSnoozeTime = 60 * 5;//休憩中止の提案の周期
    final private int breakActionTime = 60 * 1;//休憩中の動作の周期
    private boolean timerStopFrag;//毎秒呼び出されるタイマースレッドが停止しているかを表すフラグ(false:動作中 true:停止中)
    private boolean phaseFrag;//現在のフェイズを表すフラグ(false:break true:work)
    private boolean alertFrag;//終了予定時刻の通知が済んだかを示すフラグ(false:未　true:済)
    private int alertTimer;//終了予定時刻までの時間をカウントダウンする
    private int suggestTimer;//フェイズの終了を提案するまでの時間をカウントダウンする
    private int actionTimer;//フェイズごとの動作を行うまでの時間をカウントダウンする

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        setContentView(R.layout.activity_main);

        //タイトルバー設定.
        //setupTitleBar();

        //ホームボタンの検知登録.
        mHomeEventReceiver = new HomeEventReceiver();
        IntentFilter filterHome = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mHomeEventReceiver, filterHome);

        // Prevent the keyboard from showing up on app start
        //getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // 単語変数を取得
        //inputTextValue = (EditText) findViewById(R.id.input_text_value);
        //outputTextValue = (TextView) findViewById(R.id.output_text_value);

        /*TASK
        画面のUI表示

        現在のフェイズの表示
        suggestが呼ばれるまでの残り時間の表示
        (終了予定時刻までの残り時間の表示)必要かどうか決めかねる
         */

        // フェイズ移行ボタン表示
        Button shiftPhaseButton = (Button) findViewById(R.id.voice_translate_button);
        shiftPhaseButton.setOnClickListener(view -> {
            shiftPhase();//フェイズを移行させる関数
        });

        // 終了ボタン表示
        Button finishButton = (Button) findViewById(R.id.finish_app_button);
        finishButton.setOnClickListener(view -> {
            endSession();//セッションを終了させる関数
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

        //Scene操作
        VoiceUIManagerUtil.disableScene(mVUIManager, ScenarioDefinitions.SCENE_START);

        //フラグの初期化
        timerStopFrag = false;//毎秒呼び出されるタイマースレッドが動作中かを表すフラグ(false:停止中 true:動作中)
        phaseFrag = false;//現在のフェイズを表すフラグ(false:break true:work)
        alertFrag = false;//終了予定時刻の通知が済んだかを示すフラグ(false:未　true:済)

        //workフェイズを開始
        shiftPhase();

        //毎秒起動するタイマースレッド(https://qiita.com/aftercider/items/81edf35993c2df3de353)　もしかしたらAsyncTaskクラスを使ったほうが楽かもしれない
        //Handlerインスタンスの生成
        final Handler handler = new Handler();
        TimerTask task = new TimerTask() {
            //int count = 0;
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        //スレッドの処理
                        if(!timerStopFrag) {
                            Log.v(TAG, "onTimeEvent Called");
                            onTimeEvent();//毎秒の処理をこの関数内で行う
                        }
                    }
                });
            }
        };
        Timer t = new Timer();
        t.scheduleAtFixedRate(task, 0, 1000);//1秒ごとにrunが実行される

    }

    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "onPause()");

        //バックに回ったら発話を中止する.
        VoiceUIManagerUtil.stopSpeech();

        //Scene無効化.
        VoiceUIManagerUtil.disableScene(mVUIManager, ScenarioDefinitions.SCENE_COMMON);

        //VoiceUIListenerの解除.
        VoiceUIManagerUtil.unregisterVoiceUIListener(mVUIManager, mVUIListener);

        //単一Activityの場合はonPauseでアプリを終了する.
        finish();

        /*TASK
        複数アクティビティなので即時終了はせず最終的に終了するようにする
         */
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

        System.exit(0);

        /*TASK
        複数アクティビティなのでまとめて終了するようにする
        */
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
                /*if(ScenarioDefinitions.FUNC_SEND_WORD.equals(function)) {//listenシナリオのsend_word関数
                    final String original_word = VoiceUIVariableUtil.getVariableData(variables, ScenarioDefinitions.KEY_LVCSR_BASIC);//聞いた単語をString変数に格納

                    if(!(Objects.equals(original_word, ""))) {//正常なテキストなら一連の処理を開始する
                        Log.v(TAG, "Listen Scenario Sent Normal Text");

                        //入力バーにoriginal_wordの内容を表示する
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                inputTextValue.setText(original_word);
                            }
                        });

                        startSpeakScenario(original_word);//翻訳して画面表示してspeakシナリオを開始させる
                    }else{
                        Log.v(TAG, "Listen Scenario Sent Empty Text");
                    }
                }
                if(ScenarioDefinitions.FUNC_END_SPEAK.equals(function)){//speakシナリオのend_speak関数
                    speak_flag = 0;//speakシナリオが終了したのでspeakフラグをオフにする
                    Log.v(TAG, "Speak Scenario Ended");
                }
                if(ScenarioDefinitions.FUNC_SPEAK_AGAIN.equals(function)){//againシナリオのspeak_again関数
                    Log.v(TAG, "Again Scenario Ended");
                    if(speak_again_flag == 1){//speak_againフラグが立っている、つまり一度speakシナリオが起動しているときだけ実行
                        int result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SPEAK);//speakシナリオを起動する
                        if(Objects.equals(result,VoiceUIManager.VOICEUI_ERROR)){
                            speak_again_flag = 0;//不具合時はspeak_againフラグを下げる
                            Log.v(TAG, "Speak Scenario Failed To Start");
                            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_TRANSLATE);//errorシナリオのtranslateトピックを起動する
                        }else {
                            speak_flag = 1;//speakシナリオが正常に開始したらフラグを立てる
                            Log.v(TAG, "Speak Scenario Started Again");
                        }
                    }
                }
                if(ScenarioDefinitions.FUNC_END_APP.equals(function)){//endシナリオのend_app関数
                    Log.v(TAG, "Receive End Voice Command heard");
                    finish();//アプリを終了する
                }*/
                if(ScenarioDefinitions.FUNC_SHIFT_PHASE.equals(function)) {//work_startBreakシナリオかbreak_startWorkシナリオからの呼び出し
                    shiftPhase();//フェイズを移行させる関数
                }
                if(ScenarioDefinitions.FUNC_END_SESSION.equals(function)) {//work_endシナリオかbreak_endシナリオからの呼び出し
                    endSession();//セッションを終了させる関数
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

    /*
    毎秒呼ばれるタイムイベント関数
    それぞれが同時に起きた時の処理順をどうするか
     */
    public void onTimeEvent() {
        /*定期的に呼ばれるaction
        actionTimer--   タイマーは毎秒減らしていき、1以下になっていたら処理を行う
        if 一定時間を超過
            actionのscenarioを呼ぶ　シーンごとに別
            actionTimerをリセット
         */
        actionTimer--;
        if (actionTimer < 0) {
            int result;
            if (phaseFrag) {//work状態のとき
                result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_WORK_ACTION);//アクションシナリオを起動する
                if (Objects.equals(result, VoiceUIManager.VOICEUI_ERROR)) {
                    Log.v(TAG, "Start Speech ACC_WORK_ACTION Failed");
                } else {
                    actionTimer = workActionTime;
                }
            } else {//break状態のとき
                result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_BREAK_ACTION);//アクションシナリオを起動する
                if (Objects.equals(result, VoiceUIManager.VOICEUI_ERROR)) {
                    Log.v(TAG, "Start Speech ACC_BREAK_ACTION Failed");
                } else {
                    actionTimer = breakActionTime;
                }
            }
        }

         /*終了予定時刻超過で呼ばれるalert
        if　フラグがたっていない
            alertTimer--
            if 一定時間を超過
                alertのシナリオをよぶ
                終わったらフラグを立てる
         */
        if (!alertFrag) {
            alertTimer--;
            if (alertTimer < 0) {
                int result;
                result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ALERT);//アラートシナリオを起動する
                if (Objects.equals(result, VoiceUIManager.VOICEUI_ERROR)) {
                    Log.v(TAG, "Start Speech ACC_ALERT Failed");
                } else {
                    alertFrag = true;
                }
            }
        }

        /*定期的に呼ばれるsuggest
        suggestTimer--
        if 一定時間を超過
            suggestのscenarioを呼ぶ
            suggestTimerをセット
         */
        suggestTimer--;
        if (suggestTimer < 0) {
            int result;
            if (phaseFrag) {//work状態のとき
                result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_WORK_SUGGEST);//サジェストシナリオを起動する
                if (Objects.equals(result, VoiceUIManager.VOICEUI_ERROR)) {
                    Log.v(TAG, "Start Speech ACC_WORK_SUGGEST Failed");
                } else {
                    suggestTimer = workSnoozeTime;
                }
            } else {//break状態のとき
                result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_BREAK_SUGGEST);//サジェストンシナリオを起動する
                if (Objects.equals(result, VoiceUIManager.VOICEUI_ERROR)) {
                    Log.v(TAG, "Start Speech ACC_BREAK_SUGGEST Failed");
                } else {
                    suggestTimer = breakSnoozeTime;
                }
            }
        }
    }

    /*
    フェイズを別のフェイズに移行させる関数
     */
    public void shiftPhase() {
        if(phaseFrag) {//現在workフェイズならbreakフェイズを開始する
            phaseFrag = false;//フラグをbreak状態にする

            //シーン操作
            VoiceUIManagerUtil.disableScene(mVUIManager, ScenarioDefinitions.SCENE_WORK);
            VoiceUIManagerUtil.enableScene(mVUIManager, ScenarioDefinitions.SCENE_BREAK);

            //タイマー更新
            suggestTimer = breakTime;//フェイズの終了を提案するまでの時間をカウントダウンする
            actionTimer = 0;//フェイズごとの動作を行うまでの時間をカウントダウンする
            //actionTimer = breakActionTime;//フェイズごとの動作を行うまでの時間をカウントダウンする　0にすることで、フェイズ移行後にすぐ動作をするのでわかりやすくて良くなるかも
        }else{//現在breakフェイズならworkフェイズを開始する
            phaseFrag = true;//フラグをwork状態にする

            //シーン操作
            VoiceUIManagerUtil.disableScene(mVUIManager, ScenarioDefinitions.SCENE_BREAK);
            VoiceUIManagerUtil.enableScene(mVUIManager, ScenarioDefinitions.SCENE_WORK);

            //タイマー更新
            suggestTimer = workTime;//フェイズの終了を提案するまでの時間をカウントダウンする
            actionTimer = 0;//フェイズごとの動作を行うまでの時間をカウントダウンする
            //actionTimer = workActionTime;//フェイズごとの動作を行うまでの時間をカウントダウンする
        }
    }

    /*TASK
    セッションを終了してshowActivityを開始させる関数
     */
    public void endSession() {
        timerStopFrag = true;//タイマースレッドの処理を止める　スレッド自体は残り続けてしまうのを解決したいが方法がわからない

    /*
    タイマー等を解放し、onTimeイベントが呼ばれない状態にする
    showActivityを呼び出す
    アクティビティを終了か終了待機状態にする
     */

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

            /*TASK
            他のアクティビティも終了させる必要があるか？
            */
        }
    }

    /*
     * 翻訳をしてspeakシナリオを開始させる関数
     */
    /*
    private void startSpeakScenario(final String original_word){
        if(speak_flag == 1){
            Log.v(TAG, "Speak Scenario Is During Execution");
            return;//すでにspeakシナリオが実行中の場合はリターン
        }
        if(Objects.equals(original_word,null) || original_word.length() > max_length){
            Log.v(TAG, "Original_word Is Wrong");
            speak_again_flag = 0;//不具合時はspeak_againフラグを下げる
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_TRANSLATE);//errorシナリオのtranslateトピックを起動する
            return;//original_wordが不正な場合はリターン
        }

        final String translated_word = translateSync(original_word);//original_wordを英訳したtranslated_wordを作成する
        if(translated_word.contains("Error during translation")){
            Log.v(TAG, "Translated_word Is Error Message");
            speak_again_flag = 0;//不具合時はspeak_againフラグを下げる
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_CONNECTION);//errorシナリオのconnectionトピックを起動する
            return;//translated_wordがエラーメッセージなのでリターン
        }
        if(Objects.equals(translated_word, null) || translated_word.length() > max_length){
            Log.v(TAG, "Translated_word Is Wrong");
            speak_again_flag = 0;//不具合時はspeak_againフラグを下げる
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_TRANSLATE);//errorシナリオのtranslateトピックを起動する
            return;//translated_wordが不正な場合はリターン
        }

        int result = VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_ORIGINAL_WORD, original_word);//翻訳前の単語をspeakシナリオの手が届くpメモリに送る
        if(Objects.equals(result,VoiceUIManager.VOICEUI_ERROR)){
            Log.v(TAG, "Set Original_word Failed");
            speak_again_flag = 0;//不具合時はspeak_againフラグを下げる
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_TRANSLATE);//errorシナリオのtranslateトピックを起動する
            return;//original_wordのpメモリへの保存が失敗したらリターン
        }
        result = VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_TRANSLATED_WORD, translated_word);//翻訳後の単語をspeakシナリオの手が届くpメモリに送る
        if(Objects.equals(result,VoiceUIManager.VOICEUI_ERROR)){
            Log.v(TAG, "Set Translated_word Failed");
            speak_again_flag = 0;//不具合時はspeak_againフラグを下げる
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_TRANSLATE);//errorシナリオのtranslateトピックを起動する
            return;//translated_wordのpメモリへの保存が失敗したらリターン
        }

        result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SPEAK);//speakシナリオを起動する
        if(Objects.equals(result,VoiceUIManager.VOICEUI_ERROR)){
            Log.v(TAG, "Speak Scenario Failed To Start");
            speak_again_flag = 0;//不具合時はspeak_againフラグを下げる
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_TRANSLATE);//errorシナリオのtranslateトピックを起動する
        }else{
            speak_flag = 1;//speakシナリオが正常に開始したらフラグを立てる
            speak_again_flag = 1;
            Log.v(TAG, "Speak Scenario Started");

            //出力バーにtranslated_wordの内容を表示する
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    outputTextValue.setText(translated_word);
                }
            });
        }
    }


    //日本語から英語に翻訳
    private String translateSync(String original_word) {
        final String[] translatedTextHolder = new String[1];
        CountDownLatch latch = new CountDownLatch(1);

        translate(original_word, result -> {
            translatedTextHolder[0] = result;
            latch.countDown(); // 翻訳処理が終わったサイン
        });

        try {
            latch.await(); // コールバックが終わるまで待機
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return translatedTextHolder[0]; // 翻訳結果を返す
    }

    private void translate(String original_word, TranslationResultCallback callback) {

        // 翻訳結果の言語を選択
        String targetLanguage = "en";

        // 非同期の関数を呼び出し
        /*LibreTranslateAPI.translateAsync(original_word, targetLanguage, new LibreTranslateAPI.TranslationCallback() {
            @Override
            public void onSuccess(String translatedText) {
                // Pass the translated text to the callback
                callback.onResult(translatedText);
            }

            @Override
            public void onError(String errorMessage) {
                // Pass null or an error message to the callback
                callback.onResult(null);
            }
        });*/
    }

/*
    public interface TranslationResultCallback {
        void onResult(String result);
    }
*/

    /**
     * タイトルバーを設定する.
     */
    /*private void setupTitleBar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);



        //おそらく不要

    }*/

}