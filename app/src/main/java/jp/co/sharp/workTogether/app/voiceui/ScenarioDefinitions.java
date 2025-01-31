package jp.co.sharp.workTogether.app.voiceui;

/**
 * シナリオファイルで使用する定数の定義クラス.<br>
 * <p/>
 * <p>
 * scene、memory_p(長期記憶の変数名)、resolve variable(アプリ変数解決の変数名)、accostのwordはPackage名を含むこと<br>
 * </p>
 */
public class ScenarioDefinitions {

    //static クラスとして使用する.
    private ScenarioDefinitions() {
    }

    /****************** 共通の定義 *******************/
    /**
     * sceneタグを指定する文字列
     */
    public static final String TAG_SCENE = "scene";
    /**
     * accostタグを指定する文字列
     */
    public static final String TAG_ACCOST = "accost";
    /**
     * memory_pを指定するタグ
     */
    public static final String TAG_MEMORY_P = "memory_p:";
    /**
     * target属性を指定する文字列
     */
    public static final String ATTR_TARGET = "target";
    /**
     * function属性を指定する文字列
     */
    public static final String ATTR_FUNCTION = "function";

    /****************** アプリ固有の定義 *******************/
    /**
     * Package名.
     */
    protected static final String PACKAGE = "jp.co.sharp.workTogether.app";
    /**
     * controlタグで指定するターゲット名.
     */
    public static final String TARGET = PACKAGE;
    /**
     * scene名: アプリ共通のシーン
     */
    public static final String SCENE_COMMON = PACKAGE + ".scene_common";
    /**
     * scene名: STARTシーン
     */
    public static final String SCENE_START = PACKAGE + ".scene_start";
    /**
     * scene名: SESSIONシーン
     */
    public static final String SCENE_SESSION = PACKAGE + ".scene_session";
    /**
     * scene名: WORKシーン
     */
    public static final String SCENE_WORK = PACKAGE + ".scene_work";
    /**
     * scene名: BREAKシーン
     */
    public static final String SCENE_BREAK = PACKAGE + ".scene_break";
    /**
     * scene名: SHOWシーン
     */
    public static final String SCENE_SHOW = PACKAGE + ".scene_show";
    /**
     * accost名: アプリ開始時の発話シナリオ
     */
    public static final String ACC_START_ACCOSTS = PACKAGE + ".start.accosts";
    /**
     * accost名: 予定終了時刻を通知するシナリオ
     */
    public static final String ACC_SESSION_ALERT = PACKAGE + ".session.alert";
    /**
     * accost名: セッション開始時の発話をするシナリオ
     */
    public static final String ACC_SESSION_ACCOSTS = PACKAGE + ".session.accosts";
    /**
     * accost名: 作業中の動作をするシナリオ
     */
    public static final String ACC_WORK_ACTIONS= PACKAGE + ".work.actions";
    /**
     * accost名: 休憩を促すシナリオ
     */
    public static final String ACC_WORK_SUGGEST = PACKAGE + ".work.suggestBreak";
    /**
     * accost名: 休憩中の動作をするシナリオ
     */
    public static final String ACC_BREAK_ACTIONS = PACKAGE + ".break.actions";
    /**
     * accost名: 作業再開を促すシナリオ
     */
    public static final String ACC_BREAK_SUGGEST = PACKAGE + ".break.suggestWork";
    /**
     * accost名: 出来上がった絵を見せるシナリオ
     */
    public static final String ACC_SHOW_ACCOSTS = PACKAGE + ".show.accosts";
    /**
     * 関数名：shift_phase work_startBreakシナリオ及びbreak_startWorkシナリオからSessionActivityに送る
     */
    public static final String FUNC_SHIFT_PHASE = "shift_phase";
    /**
     * 関数名：end_session session_endシナリオからSessionActivityに送る
     */
    public static final String FUNC_END_SESSION = "end_session";
    /**
     * 関数名：end_app show_endシナリオからShowActivityに送る。start_endシナリオからMainActivityにも送る
     */
    public static final String FUNC_END_APP = "end_app";
    /**
     * 関数名：use_projector show_projectorシナリオからShowActivityに送る
     */
    public static final String FUNC_USE_PROJECTOR = "use_projector";
    /**
     * 関数名：send_length start_setTimeシナリオからMainActivityに送る
     */
    public static final String FUNC_SEND_LENGTH = "send_length";
    /**
     * キー：Session_Length　send_lengthで変数を送るときのキー
     */
    public static final String KEY_SESSION_LENGTH = "Session_Length";
    /**
     * キー：Session_Length　pメモリにセッションの長さを格納するときのキー
     */
    public static final String MEM_P_SESSION_LENGTH = ScenarioDefinitions.PACKAGE + ".session_length";


}
