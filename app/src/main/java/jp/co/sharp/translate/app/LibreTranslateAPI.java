package jp.co.sharp.translate.app;

import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import javax.net.ssl.HttpsURLConnection;

public class LibreTranslateAPI {

    private static final String API_URL = "https://translate.fedilab.app/translate";

    public static void translateAsync(final String text, final String targetLanguage, final TranslationCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(API_URL);
                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String data = "q=" + URLEncoder.encode(text, "UTF-8") +
                        "&source=ja" +
                        "&target=" + URLEncoder.encode(targetLanguage, "UTF-8") +
                        "&format=text";

                System.out.println("Request data prepared: " + data);

                OutputStream os = connection.getOutputStream();
                os.write(data.getBytes("UTF-8"));
                os.close();

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "UTF-8"));
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResponse.append(errorLine);
                    }
                    errorReader.close();
                    callback.onError("Error: " + responseCode + " | " + errorResponse.toString());
                    return;
                }

                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                callback.onSuccess(jsonResponse.getString("translatedText"));
            } catch (Exception e) {
                callback.onError("Error during translation: " + e.getMessage());
            }
        }).start();
    }

    // Callback interface
    public interface TranslationCallback {
        void onSuccess(String translatedText);
        void onError(String errorMessage);
    }
}
