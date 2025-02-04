package jp.co.sharp.workTogether.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toolbar;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Random;
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
     * プロジェクタ照射状態.
     */
    private boolean isProjecting = false;
    private Handler handler;//一定間隔で呼び出される発話スレッドの制御に使用
    private Runnable runnable;//一定間隔で呼び出される発話スレッドの制御に使用
    private boolean accostStopFrag;//一定間隔で呼び出される発話スレッドが停止しているかを表すフラグ(false:動作中 true:停止中)
    private String finalElapsedTime;//セッション中に経過した時間
    private int selectedImageIndex = -1; // Store the selected image index

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

//        //プロジェクター使用ボタン取得
//        Button showProjectorButton = (Button) findViewById(R.id.show_result_button);
//        //プロジェクター使用ボタンの処理
//        showProjectorButton.setOnClickListener(view -> {
//            //ShowDrawingActivityに移動
//            startShowDrawing();
//        });
        ImageButton showProjectorButton = (ImageButton)findViewById(R.id.show_result_button);
        //第1世代ロボホンのみプロジェクターボタンを表示する.
        if(getRobohonGeneration() == 1){
            showProjectorButton.setOnClickListener(view -> {
            //ShowDrawingActivityに移動
            startShowDrawing();
        });
        }else{
            showProjectorButton.setVisibility(View.INVISIBLE);
        }

        //落書の画像の配列を作成、その後、ランダムに選んで表示させる
        ImageView imageView = (ImageView) findViewById(R.id.output_image);

        // Set a random image
        selectedImageIndex = new Random().nextInt(getImageArray().length);
        int randomImageResId = getImageArray()[selectedImageIndex];
        imageView.setImageResource(randomImageResId);
    }

    // Function to dynamically get all images with numeric names from drawable
    private int[] getImageArray() {
        List<Integer> imageList = new ArrayList<>();
        for (int i = 1; i <= 10; i++) { // Assuming you have images from 1.jpg to 10.jpg
            int resId = getResources().getIdentifier("output_image_" + i, "drawable", getPackageName());
            if (resId != 0) { // If resource exists, add it
                imageList.add(resId);
            }
        }
        // Convert List<Integer> to int[]
        int[] imageArray = new int[imageList.size()];
        for (int i = 0; i < imageList.size(); i++) {
            imageArray[i] = imageList.get(i);
        }
        return imageArray;
    }

    // Function to select a random image from the array
    private int getRandomImage() {
        int[] images = getImageArray();
        return images[new Random().nextInt(images.length)];
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

    public void startShowDrawing() {
        startProjector();
        Bundle extras = new Bundle();
        extras.putString("finalElapsedTimeLog",finalElapsedTime);
        extras.putInt("show_image_index", selectedImageIndex);
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

    public void startProjector(){
        if(!isProjecting) {//すでにプロジェクターが起動中でなければ
            Log.v(TAG, "Try Start Projector");
            //プロジェクター起動
            startService(getIntentForProjector());
            isProjecting = true;
        }else{
            Log.v(TAG, "Try Start Projector,But Projector Is Starting now");
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
     * ロボホンの世代番号を取得(SDKバージョンより判定).
     */
    private int getRobohonGeneration() {
        Log.d(TAG, "getRobohonGeneration <");

        int ret = -1;
        try {
            switch (android.os.Build.VERSION.SDK_INT){
                case 21:
                    ret = 1;
                    break;
                case 27:
                    ret = 2;
                    break;
                default:
                    ret = -1;
                    break;
            }
        } catch(Exception e) {
            Log.e(TAG, "Exception : " + e.getMessage());
        }
        Log.d(TAG, "getRobohonGeneration : ret= " + ret + " >");
        return ret;
    }

}