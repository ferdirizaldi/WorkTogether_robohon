package jp.co.sharp.translate.app.voiceui;

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
    protected static final String PACKAGE = "jp.co.sharp.translate.app";
    /**
     * controlタグで指定するターゲット名.
     */
    public static final String TARGET = PACKAGE;
    /**
     * scene名: アプリ共通のシーン
     */
    public static final String SCENE_COMMON = PACKAGE + ".scene_common";
    /**
     * accost名: アプリ開始時の発話
     */
    public static final String ACC_HELLO = PACKAGE + ".hello";
    /**
     * シナリオ：speakシナリオ
     */
    public static final String ACC_SPEAK =  ScenarioDefinitions.PACKAGE + ".speak";
    /**
     * シナリオ名：listenシナリオ
     */
    public static final String ACC_LISTEN =  ScenarioDefinitions.PACKAGE + ".listen";
    /**
     * シナリオ名：errorシナリオのconnectionトピック
     */
    public static final String ACC_ERROR_CONNECTION =  ScenarioDefinitions.PACKAGE + ".error_connection";
    /**
     * シナリオ名：errorシナリオのtranslateトピック
     */
    public static final String ACC_ERROR_TRANSLATE =  ScenarioDefinitions.PACKAGE + ".error_translate";

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
     * 関数名：end_app
     */
    public static final String FUNC_END_APP = "end_app";
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
}
