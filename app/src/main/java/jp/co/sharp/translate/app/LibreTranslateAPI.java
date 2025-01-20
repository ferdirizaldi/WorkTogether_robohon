package jp.co.sharp.translate.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;

import javax.net.ssl.HttpsURLConnection;

public class LibreTranslateAPI {
    private static final String API_URL = "https://libretranslate.de/translate";

    public static String translate(String text, String targetLanguage) {
        try {
            // Construct the URL
            URL url = new URL(API_URL);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            // Prepare data to send in the request body
            String data = "q=" + URLEncoder.encode(text, "UTF-8") +
                    "&target=" + URLEncoder.encode(targetLanguage, "UTF-8");

            // Send request data
            OutputStream os = connection.getOutputStream();
            byte[] input = data.getBytes("utf-8");
            os.write(input, 0, input.length);

            // Read the response
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }

            in.close();

            // Return translated text
            return response.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error during translation";
        }
    }
}