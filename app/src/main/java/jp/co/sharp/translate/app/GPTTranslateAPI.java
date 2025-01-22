package jp.co.sharp.translate.app;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class GPTTranslateAPI {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String API_KEY = "GPT_API_KEY"; // Replace with your GPT API key

    //GPT_API_KEY
    //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

    public static void translateAsync(final String text, final String targetLanguage, final TranslationCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(API_URL);
                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Authorization", "Bearer " + API_KEY);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "application/json");

                // Construct the GPT request body
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", "gpt-3.5-turbo");
                jsonBody.put("messages", new JSONArray()
                        .put(new JSONObject()
                                .put("role", "system")
                                .put("content", "君は通訳者だよ、日本語から " + targetLanguage + "に翻訳してね、意味も簡単に説明して."))
                        .put(new JSONObject()
                                .put("role", "user")
                                .put("content", text)));
                jsonBody.put("max_tokens", 100);

                // Send the request
                OutputStream os = connection.getOutputStream();
                os.write(jsonBody.toString().getBytes("UTF-8"));
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

                // Read the response
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                String translatedText = jsonResponse.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content").trim();

                callback.onSuccess(translatedText);

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

