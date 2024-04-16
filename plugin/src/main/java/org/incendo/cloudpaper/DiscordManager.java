package org.incendo.cloudpaper;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DiscordManager {

//    private final String webhookUrl;
//    private final FileConfiguration config;
    public static void main(String[] args) {
        PostTicketToDiscord("Create", "1", "d783e971-e1c8-4c34-bdc7-1146b798ac38", "Reeceboy1299", "This is a testing message", "320948203984231123");
    }
//    public DiscordManager(String webhookUrl, FileConfiguration config) {
//        this.config = config;
//        this.webhookUrl = this.config.getString("webhook");
//    }

    public static void PostTicketToDiscord(String event, String id, String userId, String username, String message, String discordId) {
        try {
            URL url = new URL("http://localhost:5000/webhook");
            HttpURLConnection con = (HttpURLConnection)url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);
            String jsonInputString =
                    "{\"event\": \"" + event + "\", " +
                    "\"id\": \"" + id + "\", " +
                    "\"user-uuid\": \"" + userId + "\", " +
                    "\"username\": \"" + username + "\", " +
                    "\"message\": \"" + message + "\", " +
                    "\"discord-id\": \"" + discordId + "\"}";
            try(OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            try(BufferedReader br = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println(response.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
