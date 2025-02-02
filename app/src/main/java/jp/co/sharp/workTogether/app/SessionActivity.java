package jp.co.sharp.workTogether.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;//追加日1/24
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
import java.util.concurrent.Executors;//追加日1/24
import java.util.concurrent.ScheduledExecutorService;//追加日1/24
import java.util.concurrent.TimeUnit;//追加日1/24
import java.util.Timer;//追加日1/24
import java.util.TimerTask;//追加日1/24
import java.util.Random;//追加日1/27

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
    final private int workActionTimeFirst = 7;//初回の作業動作までの時間(秒)
    final private int workActionTime = 60 * 1;//作業中の動作の周期(秒)
    final private int breakSuggestTimeFirst = 60 * 5;//初回の休憩中止の提案までの時間(秒)
    final private int breakSuggestTime = 60 * 5;//休憩中止の提案の周期(秒)
    final private int breakActionTimeFirst = 7;//初回の休憩動作までの時間(秒)
    final private int breakActionTime = 60 * 1;//休憩中の動作の周期(秒)
    private boolean phaseFrag;//現在のフェイズを表すフラグ(false:break true:work)
    private boolean alertFrag;//終了予定時刻の通知が済んだかを示すフラグ(false:未　true:済)
    private int alertTimer;//終了予定時刻までの時間をカウントダウンするタイマー
    private int suggestTimer;//フェイズの終了を提案するまでの時間をカウントダウンするタイマー
    private int actionTimer;//フェイズごとの動作を行うまでの時間をカウントダウンするタイマー
    private int sessionLength;//meinActivityから送られてくる、セッション終了までの時間
    private int totalTimer;//全体のカウントアップタイマー
    private String startTime;//セッション開始時の時刻

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        setContentView(R.layout.session_activity);

        //ホームボタンの検知登録.
        mHomeEventReceiver = new HomeEventReceiver();
        IntentFilter filterHome = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mHomeEventReceiver, filterHome);//mainActivityでも同様の警告が出ている

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
        Button shiftPhaseButton = (Button) findViewById(R.id.shift_phase_button);
        Button sessionFinishButton = (Button) findViewById(R.id.finish_session_button);

        // Text Areas
        TextView sessionOutputStatus = (TextView) findViewById(R.id.sessionOutput_text1_value);
        TextView sessionOutputTime = (TextView) findViewById(R.id.sessionOutput_text2_value);


        //タイマー値の設定
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //現在のフェイズを表す
                sessionOutputStatus.setText("作業中");
                //終了予定時刻を表す
                sessionOutputTime.setText("∞");
            }
        });

        updateSessionOutputTime(sessionLength, sessionOutputTime);

        shiftPhaseButton.setOnClickListener(v -> {
            //作業フェイズと休憩フェイズを切り替えるボタン
            shiftPhase();
        });

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

        // Get the current time
        Calendar calendar = Calendar.getInstance();

        // Add the session duration in hours to the current time
        if (sessionLength == 0) {
            // Handle the default case (e.g., infinite session)
            return;
        } else{
            calendar.add(Calendar.HOUR_OF_DAY, sessionLength);
        }

        String endTime = timeFormat.format(calendar.getTime());

        // Update the TextViews on the UI thread
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sessionOutputTime.setText(endTime);
            }
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
        VoiceUIManagerUtil.enableScene(mVUIManager, ScenarioDefinitions.SCENE_COMMON);
        VoiceUIManagerUtil.enableScene(mVUIManager, ScenarioDefinitions.SCENE_SESSION);

        //meinActivityのintentからextrasを取得し、アラートタイマーを設定し、そのフラグを設定
        alertTimer = 3600 * sessionLength;//単位を時間から秒に変換
        Log.v(TAG, "Session AlertTime:" + alertTimer);
        alertFrag = (alertTimer == 0);//アラートまでの時間が未定義等により0秒になった時は、すでにアラート済みということにしてタイマーを止める

        //セッション開始の発話
        if(alertFrag) {//時間が設定されていないときは発話内容に時間の含まれていないトピックを呼び出す
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SESSION_ACCOSTS + ".t2");
        }else{//時間が設定されているときは発話内容に時間の含まれているトピックを呼び出す
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SESSION_ACCOSTS + ".t1");
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
                    Log.v(TAG, "onTimeEvent Called");
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
    それぞれが同時に起きた時の処理順をどうするかを考える必要あり
     */
    public void onTimeEvent() {
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
                result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SESSION_ALERT);//アラートシナリオを起動する
                if (Objects.equals(result, VoiceUIManager.VOICEUI_ERROR)) {
                    Log.v(TAG, "Start Speech ACC_ALERT Failed");
                } else {
                    alertFrag = true;
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
        if (actionTimer < 0) {
            int result;
            if (phaseFrag) {//work状態のとき
                int rnd = new Random().nextInt(2) + 1;//複数あるトピックのうち一つをランダムに選んで呼ぶ(0~指定した数未満の整数がかえってくるので1足している)
                result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_WORK_ACTIONS + ".t" + rnd);//アクションシナリオを起動する
                if (Objects.equals(result, VoiceUIManager.VOICEUI_ERROR)) {
                    Log.v(TAG, "Start Speech ACC_WORK_ACTION Failed");
                } else {
                    actionTimer = workActionTime;
                }
            } else {//break状態のとき
                int rnd = new Random().nextInt(2) + 1;//複数あるトピックのうち一つをランダムに選んで呼ぶ(0~指定した数未満の整数がかえってくるので1足している)
                result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_BREAK_ACTIONS + ".t" + rnd);//アクションシナリオを起動する
                if (Objects.equals(result, VoiceUIManager.VOICEUI_ERROR)) {
                    Log.v(TAG, "Start Speech ACC_BREAK_ACTION Failed");
                } else {
                    actionTimer = breakActionTime;
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
                    suggestTimer = workSuggestTime;
                }
            } else {//break状態のとき
                result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_BREAK_SUGGEST);//サジェストンシナリオを起動する
                if (Objects.equals(result, VoiceUIManager.VOICEUI_ERROR)) {
                    Log.v(TAG, "Start Speech ACC_BREAK_SUGGEST Failed");
                } else {
                    suggestTimer = breakSuggestTime;
                }
            }
        }

        // 4. Clock Logic 全体カウントアップタイマー更新
        totalTimer++;
        String timeString = formatTime(totalTimer);
        Log.v(TAG, "Current Time: " + timeString); // Log or update UI with the time string
        // 休憩に入る、UI更新
        TextView sessionOutputStatus = (TextView) findViewById(R.id.sessionOutput_text1_value2);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sessionOutputStatus.setText(timeString);
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


            // 休憩に入る、UI更新
            TextView sessionOutputStatus = (TextView) findViewById(R.id.sessionOutput_text1_value);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    sessionOutputStatus.setText("休憩");
                    sessionOutputStatus.setTextColor(Color.YELLOW);
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

            // 作業に入る、UI更新
            TextView sessionOutputStatus = (TextView) findViewById(R.id.sessionOutput_text1_value);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    sessionOutputStatus.setText("作業中");
                    sessionOutputStatus.setTextColor(Color.GREEN);
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
            finish();//mainActivityがSessionActivityを呼び出した後に速やかに終了していればブロードキャストを用いる必要もない
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