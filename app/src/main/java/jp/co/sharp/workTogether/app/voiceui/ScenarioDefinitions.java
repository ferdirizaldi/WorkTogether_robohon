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
    public static final String ACC_HELLO = PACKAGE + ".start_hello";
    /**
     * accost名: 作業中の動作をするシナリオ
     */
    public static final String ACC_WORK_ACTION= PACKAGE + ".work.action";
    /**
     * accost名: 休憩を促すシナリオ
     */
    public static final String ACC_WORK_SUGGEST = PACKAGE + ".work.suggestBreak";
    /**
     * accost名: 休憩中の動作をするシナリオ
     */
    public static final String ACC_BREAK_ACTION= PACKAGE + ".break.action";
    /**
     * accost名: 作業再開を促すシナリオ
     */
    public static final String ACC_BREAK_SUGGEST = PACKAGE + ".break.suggestWork";
    /**
     * accost名: 出来上がった絵を見せるシナリオ
     */
    public static final String ACC_SHOW_ACCOST = PACKAGE + ".show.accost";
    /**
     * accost名: 予定終了時刻を通知するシナリオ
     */
    public static final String ACC_ALERT = PACKAGE + ".alert";
    /**
     * 関数名：shift_phase work_startBreakシナリオ及びbreak_startWorkシナリオからSessionActivityに送る
     */
    public static final String FUNC_SHIFT_PHASE = "shift_phase";
    /**
     * 関数名：end_session work_endシナリオ及びbreak_endシナリオからSessionActivityに送る
     */
    public static final String FUNC_END_SESSION = "end_session";
    /**
     * 関数名：end_app show_endシナリオからShowActivityに送る
     */
    public static final String FUNC_END_APP = "end_app";
    /**
     * 関数名：use_projector show_projectorシナリオからShowActivityに送る
     */
    public static final String FUNC_USE_PROJECTOR = "use_projector";

    /**
     * 関数名：send_word
     */
    public static final String FUNC_SEND_WORD = "send_word";
    /**
     * 関数名：end_speak
     */
    public static final String FUNC_END_SPEAK = "end_speak";
    /**
     * 関数名：end_app
     */
    public static final String FUNC_SPEAK_AGAIN = "speak_again";
    /**
     * キー：lvcsr_basic
     */
    public static final String KEY_LVCSR_BASIC = "Lvcsr_Basic";
    /**
     * 翻訳前の単語
     */
    public static final String MEM_P_ORIGINAL_WORD = ScenarioDefinitions.PACKAGE + ".original_word";
    /**
     * 翻訳後の単語
     */
    public static final String MEM_P_TRANSLATED_WORD = ScenarioDefinitions.PACKAGE + ".translated_word";
    /**
     *
     */
    public static final String START_END_SPEAK = ScenarioDefinitions.PACKAGE + ".start_end";

}
