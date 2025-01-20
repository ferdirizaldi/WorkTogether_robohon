package jp.co.sharp.translate.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;//追加1/17 multilingualからのコピペ
import android.util.Log;
import android.view.View;//追加1/17 multilingualからのコピペ
import android.widget.Button;//追加1/17 multilingualからのコピペ
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toolbar;

import java.util.List;
import java.util.Locale;//追加1/17 multilingualからのコピペ

import jp.co.sharp.android.voiceui.VoiceUIManager;
import jp.co.sharp.android.voiceui.VoiceUIVariable;
import jp.co.sharp.translate.app.voiceui.ScenarioDefinitions;
import jp.co.sharp.translate.app.voiceui.VoiceUIListenerImpl;
import jp.co.sharp.translate.app.voiceui.VoiceUIManagerUtil;
import jp.co.sharp.translate.app.voiceui.VoiceUIVariableUtil;//追加1/17 multilingualからのコピペ


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

    private EditText inputTextValue;
    private TextView outputTextValue;
    private int speak_flag = 0;//speakシナリオ実行中に立つフラグ

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        setContentView(R.layout.activity_main);

        //タイトルバー設定.
        setupTitleBar();

        //ホームボタンの検知登録.
        mHomeEventReceiver = new HomeEventReceiver();
        IntentFilter filterHome = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mHomeEventReceiver, filterHome);

        //
        //ボタン等を作る
        //

        // 単語変数を取得
        inputTextValue = (EditText) findViewById(R.id.input_text_value);
        outputTextValue = (TextView) findViewById(R.id.output_text_value);

        // 翻訳ボタン表示
        Button voiceTranslateButton = (Button) findViewById(R.id.voice_translate_button);
        voiceTranslateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleTextProcessing();
            }
        });

    }

    /**
     * Handle the text processing when the button is clicked.
     */
    private void handleTextProcessing() {
        // Get the input text
        String inputText = inputTextValue.getText().toString().trim();

        // Perform text processing (e.g., mock translation or processing)
        String processedText = processText(inputText);

        // Display the processed text in the output box
        outputTextValue.setText(processedText);
    }

    private String processText(String input) {
        if (input.isEmpty()) {
            return "Please enter some text!";
        }
        // Example: Reverse the input text (replace with your logic)
        return new StringBuilder(input).reverse().toString();
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

        //アプリ起動時に発話を実行.
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
                    final String jp_word = VoiceUIVariableUtil.getVariableData(variables, ScenarioDefinitions.KEY_LVCSR_BASIC);//聞いた単語をString変数に格納
                    //
                    //入力バーにjp_wordの内容を表示する
                    //
                    startSpeakScenario(jp_word);//speakシナリオを開始させる
                }
                if(ScenarioDefinitions.FUNC_END_SPEAK.equals(function)){//speakシナリオのend_speak関数
                    speak_flag = 0;//speakシナリオが終了したのでspeakフラグをオフにする
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
    private void startSpeakScenario(final String jp_word){
        if(speak_flag == 1)return;//すでにspeakシナリオが実行中の場合はリターン
        if(jp_word == null || jp_word.length() > 100)return;//jp_wordが不正な場合はリターン

        speak_flag = 1;//speakシナリオを開始したら立てる

        final String en_word = translate(jp_word);//jp_wordを英訳したen_wordを作成する
        if(en_word == null || en_word.length() > 100){//en_wordが不正な場合はフラグを下げてからリターン
            speak_flag = 0;
            return;
        }

        VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_JP_WORD, jp_word);
        VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_EN_WORD, en_word);//翻訳前と済みの単語をspeakシナリオの手が届くpメモリに送る
        VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SPEAK);//speakシナリオを起動する
    }


    //日本語から英語に翻訳
    private String translate(String jp_word) {
        if (jp_word.isEmpty()) {
            outputTextValue.setText("Please enter some text!");
            return null;
        }
        // 翻訳APIのインスタンス生成
        LibreTranslateAPI translateService = new LibreTranslateAPI();

        // 翻訳関数呼び出し
        String targetLanguage = "en"; // Example: Spanish
        String translatedText = translateService.translate(jp_word, targetLanguage);

        // 出力表示
        outputTextValue.setText(translatedText);

        return translatedText;
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
     * タイトルバーを設定する.
     */
    private void setupTitleBar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);
    }

}