package jp.co.sharp.workTogether.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toolbar;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Random;

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
    private Handler handler;//毎秒呼び出されるスレッドの制御に使用
    private Runnable runnable;//毎秒呼び出されるスレッドの制御に使用
    private boolean timerStopFrag;//毎秒呼び出されるタイマースレッドが停止しているかを表すフラグ(false:動作中 true:停止中)
    final private int workSuggestTimeFirst = 60 * 25;//初回の作業中止の提案までの時間(秒)
    final private int workSuggestTime = 60 * 5;//作業中止の提案の周期(秒)
    final private int workActionTimeFirst = 10;//初回の作業動作までの時間(秒)
    final private int workActionTime = 30;//作業中の動作の周期(秒)
    final private int breakSuggestTimeFirst = 60 * 5;//初回の休憩中止の提案までの時間(秒)
    final private int breakSuggestTime = 60 * 5;//休憩中止の提案の周期(秒)
    final private int breakActionTimeFirst = 10;//初回の休憩動作までの時間(秒)
    final private int breakActionTime = 30;//休憩中の動作の周期(秒)
    private boolean phaseFrag;//現在のフェイズを表すフラグ(false:break true:work)
    private boolean alertFrag;//終了予定時刻の通知が済んだかを示すフラグ(false:未　true:済)
    private int alertTimer;//終了予定時刻までの時間をカウントダウンするタイマー
    private int suggestTimer;//フェイズの終了を提案するまでの時間をカウントダウンするタイマー
    private int actionTimer;//フェイズごとの動作を行うまでの時間をカウントダウンするタイマー
    private int sessionLength;//meinActivityから送られてくる、セッション終了までの時間
    private int phaseTimer;//フェイズ中の経過時間を表すカウントアップタイマー
    private String startTime;//セッション開始時の時刻
    private boolean accostingFrag;//発話中であることを表すフラグ
    private String accostingName;//発話中のシナリオの名前

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        setContentView(R.layout.session_activity);

        //ホームボタンの検知登録.
        mHomeEventReceiver = new HomeEventReceiver();
        IntentFilter filterHome = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mHomeEventReceiver, filterHome);

        //セッションの予定時間sessionLengthを受け取る
        sessionLength = getIntentIntDataByKey("sessionLength");
        Log.v(TAG, "Session Length:" + sessionLength);

        // UI表示
        initializeSessionUI();

    }

    /**
     * Retrieves the extras from the intent and returns them
     *
     * @return A int data is the session data or 0 if no extras exist by key.
     * //Intentからのint型変数を受け取る関数
     */
    private int getIntentIntDataByKey(String key) {
        Intent intent = getIntent();
        if (intent != null) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                return extras.getInt(key, 0);
            }
        }

        // Return null if no extras are found
        Log.v("Session Activity", "No Extras Are Found");
        return 0;
    }

    /**
     * Initializes buttons and their click listeners.
     */
    private void initializeSessionUI() {
        // Button references
        Button finishButton = (Button) findViewById(R.id.finish_app_button);
        Button sessionFinishButton = (Button) findViewById(R.id.finish_session_button);

        // Text Areas
        TextView sessionOutputTime = (TextView) findViewById(R.id.sessionOutput_text2_value);

        updateSessionOutputTime(sessionLength, sessionOutputTime);

        sessionFinishButton.setOnClickListener(v -> {
            // セッションを終了させるボタン
            endSession();//SessionActivityを終了させShowActivityに遷移
        });

        finishButton.setOnClickListener(v -> {
            // アプリ自体を終了させるボタン
            finish();
        });
    }

    private void updateSessionOutputTime(int sessionLength, TextView sessionOutputTime) {

        //現在時刻取得
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        // Format the calculated end time to HH:mm:ss
        startTime = timeFormat.format(Calendar.getInstance().getTime());
        Log.v(TAG,"startTime is " + startTime);

        if (sessionLength == 0) {
            // Handle the default case (e.g., infinite session)
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    sessionOutputTime.setText("時間指定無し");
                }
            });
        } else {
            // Get the current time
            Calendar calendar = Calendar.getInstance();

            // Add the session duration in hours to the current time
            calendar.add(Calendar.HOUR_OF_DAY, sessionLength);
            String endTime = timeFormat.format(calendar.getTime());

            // Update the TextViews on the UI thread
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    sessionOutputTime.setText(endTime);
                }
            });
        }
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
        VoiceUIManagerUtil.enableScene(mVUIManager, ScenarioDefinitions.SCENE_COMMON);
        VoiceUIManagerUtil.enableScene(mVUIManager, ScenarioDefinitions.SCENE_SESSION);

        //meinActivityのintentからextrasを取得し、アラートタイマーを設定し、そのフラグを設定
        alertTimer = 3600 * sessionLength;//単位を時間から秒に変換
        Log.v(TAG, "Session AlertTime:" + alertTimer);
        alertFrag = (alertTimer == 0);//アラートまでの時間が未定義等により0秒になった時は、すでにアラート済みということにしてタイマーを止める

        //セッション開始の発話
        if(alertFrag) {//時間が設定されていないときは発話内容に時間の含まれていないトピックを呼び出す
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SESSION_ACCOSTS + ".t2");
            accostingFrag = true;//発話中であることを表すフラグ
            accostingName = ScenarioDefinitions.ACC_SESSION_ACCOSTS + ".t2";//発話中のシナリオの名前
        }else{//時間が設定されているときは発話内容に時間の含まれているトピックを呼び出す
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SESSION_ACCOSTS + ".t1");
            accostingFrag = true;//発話中であることを表すフラグ
            accostingName = ScenarioDefinitions.ACC_SESSION_ACCOSTS + ".t1";//発話中のシナリオの名前
        }

        //breakフェイズからフェイズ移行させることでworkフェイズを開始
        phaseFrag = false;//現在のフェイズを表すフラグ(false:break true:work)
        shiftPhase();

        //毎秒起動するタイマースレッド(https://qiita.com/aftercider/items/81edf35993c2df3de353)
        timerStopFrag = false;//毎秒呼び出されるタイマースレッドが停止しているかを表すフラグ(false:動作中 true:停止中)
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                // UIスレッド
                if(!timerStopFrag) {
                    //Log.v(TAG, "onTimeEvent Called");
                    onTimeEvent();//毎秒の処理をこの関数内で行う
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.post(runnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "onPause()");

        //バックに回ったら発話を中止する.
        VoiceUIManagerUtil.stopSpeech();

        //Scene無効化.
        VoiceUIManagerUtil.disableScene(mVUIManager, ScenarioDefinitions.SCENE_COMMON);
        VoiceUIManagerUtil.disableScene(mVUIManager, ScenarioDefinitions.SCENE_SESSION);
        VoiceUIManagerUtil.disableScene(mVUIManager, ScenarioDefinitions.SCENE_WORK);
        VoiceUIManagerUtil.disableScene(mVUIManager, ScenarioDefinitions.SCENE_BREAK);

        //VoiceUIListenerの解除.
        VoiceUIManagerUtil.unregisterVoiceUIListener(mVUIManager, mVUIListener);

        timerStopFrag = true;//毎秒呼び出されるタイマースレッドが停止しているかを表すフラグ(false:動作中 true:停止中)
        handler.removeCallbacks(runnable);//毎秒呼び出されるスレッドの呼び出し予約を破棄する

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
                accostingFrag = false;//発話中であることを表すフラグ
                accostingName = "";//発話中のシナリオの名前
                if(ScenarioDefinitions.FUNC_SHIFT_PHASE.equals(function)) {//work_startBreakシナリオかbreak_startWorkシナリオからの呼び出し
                    shiftPhase();//フェイズを移行させる関数
                }
                if(ScenarioDefinitions.FUNC_END_SESSION.equals(function)) {//work_endシナリオかbreak_endシナリオからの呼び出し
                    endSession();//セッションを終了させる関数
                }
                if(ScenarioDefinitions.FUNC_ALERTED.equals(function)) {//session_alertシナリオからの呼び出し
                    alertFrag = true;//アラートが発話済みなのでフラグを立てる
                }
                if(ScenarioDefinitions.FUNC_WORK_ACTIONED.equals(function)) {//work_actionsシナリオからの呼び出し
                    if(phaseFrag) {//workフェイズ中なら
                        actionTimer = workActionTime;//動作を終えたので次の動作までのタイマーをセット
                    }
                }
                if(ScenarioDefinitions.FUNC_BREAK_ACTIONED.equals(function)) {//break_actionsシナリオからの呼び出し
                    if(!phaseFrag) {//breakフェイズ中なら
                        actionTimer = breakActionTime;//動作を終えたので次の動作までのタイマーをセット
                    }
                }
                if(ScenarioDefinitions.FUNC_WORK_SUGGESTED.equals(function)) {//work_suggestBreakシナリオからの呼び出し
                    if(phaseFrag) {//workフェイズ中なら
                        suggestTimer = workSuggestTime;//提案シナリオを発話し終えたので次の発話までのタイマーをセット
                    }
                }
                if(ScenarioDefinitions.FUNC_BREAK_SUGGESTED.equals(function)) {//break_suggestBreakシナリオからの呼び出し
                    if(!phaseFrag) {//breakフェイズ中なら
                        suggestTimer = breakSuggestTime;//提案シナリオを発話し終えたので次の発話までのタイマーをセット
                    }
                }
                break;
            case VoiceUIListenerImpl.ACTION_START:
                accostingFrag = true;//発話中であることを表すフラグ
                accostingName = VoiceUIVariableUtil.getVariableData(variables, ScenarioDefinitions.TAG_ACCOST);//発話中のシナリオの名前
                break;
            case VoiceUIListenerImpl.ACTION_CANCELLED:
            case VoiceUIListenerImpl.ACTION_REJECTED:
                if(Objects.equals(accostingName, VoiceUIVariableUtil.getVariableData(variables, ScenarioDefinitions.TAG_ACCOST))){//発話が棄却やキャンセルされた場合は立てたフラグ等を戻す
                    accostingFrag = false;//発話中であることを表すフラグ
                    accostingName = "";//発話中のシナリオの名前
                }
                //他のシナリオの発話中に発話が棄却された場合は何もしない
                break;
            case VoiceUIListenerImpl.RESOLVE_VARIABLE:
            default:
                break;
        }
    }

    /*
    毎秒呼ばれるタイムイベント関数
     */
    public void onTimeEvent() {
          /*終了予定時刻超過で呼ばれるalert
        if　フラグがたっていない
            alertTimer--
            if 一定時間を超過
                alertのシナリオをよぶ
         */
        if (!alertFrag) {
            Log.v(TAG, "AlertTimer:"+alertTimer);
            alertTimer--;
            if (alertTimer < 0 && !accostingFrag) {
                int result;
                result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SESSION_ALERT);//アラートシナリオを起動する
                if (Objects.equals(result, VoiceUIManager.VOICEUI_ERROR)) {
                    Log.v(TAG, "Start Speech ACC_ALERT Failed");
                }else {
                    accostingFrag = true;//発話中であることを表すフラグ
                    accostingName = ScenarioDefinitions.ACC_SESSION_ALERT;//発話中のシナリオの名前
                }
            }
        }

        /*定期的に呼ばれるaction
        actionTimer--   タイマーは毎秒減らしていき、1以下になっていたら処理を行う
        if 一定時間を超過
            actionのscenarioを呼ぶ　シーンごとに別
            actionTimerをリセット
         */
        actionTimer--;
        Log.v(TAG, "ActionTimer:"+actionTimer);
        if (actionTimer < 0 && !accostingFrag) {
            int result;
            if (phaseFrag) {//work状態のとき
                if(alertTimer < (3600 * sessionLength) * 0.2){//もうちょっとで終了時間になる時(残り時間がセッション時間の20％未満の時)は"work_actions"のt8~t10で発話させる
                    int rnd = new Random().nextInt(3) + 8;//"work_actions"のt8~t10のうちランダムの数字を出す
                    result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_WORK_ACTIONS + ".t" + rnd);//アクションシナリオを起動する
                    if (Objects.equals(result, VoiceUIManager.VOICEUI_ERROR)) {
                        Log.v(TAG, "Start Speech ACC_WORK_ACTION Failed");
                    }else{
                        accostingFrag = true;//発話中であることを表すフラグ
                        accostingName = ScenarioDefinitions.ACC_WORK_ACTIONS + ".t" + rnd;//発話中のシナリオの名前
                    }
                }else{//通常の時
                    int rnd = new Random().nextInt(7) + 1;//複数あるトピックのうち一つをランダムに選んで呼ぶ(0~指定した数未満の整数がかえってくるので1足している)
                    result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_WORK_ACTIONS + ".t" + rnd);//アクションシナリオを起動する
                    if (Objects.equals(result, VoiceUIManager.VOICEUI_ERROR)) {
                        Log.v(TAG, "Start Speech ACC_WORK_ACTION Failed");
                    }else{
                        accostingFrag = true;//発話中であることを表すフラグ
                        accostingName = ScenarioDefinitions.ACC_WORK_ACTIONS + ".t" + rnd;//発話中のシナリオの名前
                    }
                }
            } else {//break状態のとき
                int rnd = new Random().nextInt(6) + 1;//複数あるトピックのうち一つをランダムに選んで呼ぶ(0~指定した数未満の整数がかえってくるので1足している)
                result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_BREAK_ACTIONS + ".t" + rnd);//アクションシナリオを起動する
                if (Objects.equals(result, VoiceUIManager.VOICEUI_ERROR)) {
                    Log.v(TAG, "Start Speech ACC_BREAK_ACTION Failed");
                }else{
                    accostingFrag = true;//発話中であることを表すフラグ
                    accostingName = ScenarioDefinitions.ACC_BREAK_ACTIONS + ".t" + rnd;//発話中のシナリオの名前
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
        Log.v(TAG, "SuggestTimer:"+suggestTimer);
        if (suggestTimer < 0 && !accostingFrag) {
            int result;
            if (phaseFrag) {//work状態のとき
                int rnd = new Random().nextInt(3) + 1;
                result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_WORK_SUGGEST + "t." + rnd);//サジェストシナリオを起動する
                if (Objects.equals(result, VoiceUIManager.VOICEUI_ERROR)) {
                    Log.v(TAG, "Start Speech ACC_WORK_SUGGEST Failed");
                }else{
                    accostingFrag = true;//発話中であることを表すフラグ
                    accostingName = ScenarioDefinitions.ACC_WORK_SUGGEST;//発話中のシナリオの名前
                }
            } else {//break状態のとき
                int rnd = new Random().nextInt(3) + 1;
                result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_BREAK_SUGGEST + "t." + rnd);//サジェストンシナリオを起動する
                if (Objects.equals(result, VoiceUIManager.VOICEUI_ERROR)) {
                    Log.v(TAG, "Start Speech ACC_BREAK_SUGGEST Failed");
                }else{
                    accostingFrag = true;//発話中であることを表すフラグ
                    accostingName = ScenarioDefinitions.ACC_BREAK_SUGGEST;//発話中のシナリオの名前
                }
            }
        }

        // 4. Clock Logic フェイズ中経過時間カウントアップタイマー更新
        phaseTimer++;
        String timeString = formatTime(phaseTimer);
        //Log.v(TAG, "Current Time: " + timeString); // Log or update UI with the time string
        //UI更新
        TextView sessionOutputStatus2 = (TextView) findViewById(R.id.sessionOutput_text1_value2);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sessionOutputStatus2.setText(timeString);
            }
        });

    }

    /*
    秒からhh:mm:ssに変更する関数
     */
    private String formatTime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /*
    フェイズを別のフェイズに移行させる関数
     */
    public void shiftPhase() {
        if(phaseFrag) {//現在workフェイズならbreakフェイズを開始する
            Log.v(TAG, "Start Break Phase");
            phaseFrag = false;//フラグをbreak状態にする

            //シーン操作
            VoiceUIManagerUtil.disableScene(mVUIManager, ScenarioDefinitions.SCENE_WORK);
            VoiceUIManagerUtil.enableScene(mVUIManager, ScenarioDefinitions.SCENE_BREAK);

            //タイマー更新
            suggestTimer = breakSuggestTimeFirst;//フェイズの終了を提案するまでの時間をカウントダウンする
            actionTimer = breakActionTimeFirst;//フェイズごとの初めての動作を行うまでの時間をカウントダウンする
            //フェイズ中の経過時間を表すカウントアップタイマーを初期化
            phaseTimer = 0;

            // 休憩に入る、UI更新
            TextView sessionOutputStatus = (TextView) findViewById(R.id.sessionOutput_text1_value);
            Button shiftPhaseButton = (Button) findViewById(R.id.shift_phase_button);
            shiftPhaseButton.setOnClickListener(v -> {
                //作業フェイズと休憩フェイズを切り替えるボタン
                shiftPhase();
            });
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    sessionOutputStatus.setText("休憩中");
                    sessionOutputStatus.setTextColor(Color.YELLOW);
                    shiftPhaseButton.setText("作業を再開する");
                }
            });

        }else{//現在breakフェイズならworkフェイズを開始する
            Log.v(TAG, "Start Work Phase");
            phaseFrag = true;//フラグをwork状態にする

            //シーン操作
            VoiceUIManagerUtil.disableScene(mVUIManager, ScenarioDefinitions.SCENE_BREAK);
            VoiceUIManagerUtil.enableScene(mVUIManager, ScenarioDefinitions.SCENE_WORK);

            //タイマー更新
            suggestTimer = workSuggestTimeFirst;//フェイズの終了を提案するまでの時間をカウントダウンする
            actionTimer = workActionTimeFirst;//フェイズごとの初めての動作を行うまでの時間をカウントダウンする
            //フェイズ中の経過時間を表すカウントアップタイマーを初期化
            phaseTimer = 0;

            // 作業に入る、UI更新
            TextView sessionOutputStatus = (TextView) findViewById(R.id.sessionOutput_text1_value);
            Button shiftPhaseButton = (Button) findViewById(R.id.shift_phase_button);
            shiftPhaseButton.setOnClickListener(v -> {
                //作業フェイズと休憩フェイズを切り替えるボタン
                shiftPhase();
            });
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    sessionOutputStatus.setText("作業中");
                    sessionOutputStatus.setTextColor(Color.GREEN);
                    shiftPhaseButton.setText("休憩を開始する");
                }
            });
        }
    }

    /*
    セッションを終了してshowActivityを開始させる関数
     */
    public void endSession() {
        /*
    タイマー等を解放し、onTimeイベントが呼ばれない状態にする
    showActivityを呼び出す
    アクティビティを終了する
     */
        Bundle extras = new Bundle();
        extras.putString("sessionStartTime", startTime);
        extras.putString("checkFirst", "first");
        navigateToActivity(this, ShowActivity.class, extras);//ShowActivityを呼び出す

        finish();//ShowActivityを呼んだらすぐに終了する
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