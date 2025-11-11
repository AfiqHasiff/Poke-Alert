package com.afiqhasiff.pokealert.client.notification;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.config.ConfigManager;
import com.afiqhasiff.pokealert.client.config.TelegramConfig;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Telegram notification service.
 * Sends notifications to Telegram via Bot API.
 */
public class TelegramNotification extends NotificationService {
    private TelegramConfig config;
    private OkHttpClient httpClient;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault());

    // Rate limiting
    private int notificationCount = 0;
    private long lastResetTime = System.currentTimeMillis();

    @Override
    public void initialize() {
        config = ConfigManager.getTelegramConfig();
        
        // Create HTTP client with reasonable timeouts
        httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

        if (config.isValid()) {
            PokeAlertClient.LOGGER.info("Telegram notification service initialized");
        } else {
            PokeAlertClient.LOGGER.warn("Telegram notification service initialized but not configured");
        }
    }

    @Override
    public void sendNotification(PokemonSpawnData data) {
        // Check if Telegram is enabled in PokéAlert config
        if (!PokeAlertClient.getInstance().config.telegramEnabled || !config.isValid()) {
            return;
        }

        // Check rate limiting
        if (!checkRateLimit()) {
            PokeAlertClient.LOGGER.warn("Telegram rate limit exceeded, skipping notification");
            return;
        }

        // Format message
        String message = formatMessage(data);

        // Build request
        String jsonBody = String.format(
            "{\"chat_id\":\"%s\",\"text\":\"%s\",\"parse_mode\":\"HTML\"}",
            escapeJson(config.getChatId()),
            escapeJson(message)
        );

        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request request = new Request.Builder()
            .url(config.getSendMessageUrl())
            .post(body)
            .build();

        // Send async
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                PokeAlertClient.LOGGER.error("Failed to send Telegram notification: {}", e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    PokeAlertClient.LOGGER.debug("Telegram notification sent successfully");
                } else {
                    PokeAlertClient.LOGGER.error(
                        "Telegram API returned error: {} - {}",
                        response.code(),
                        response.body() != null ? response.body().string() : "No body"
                    );
                }
                response.close();
            }
        });
    }

    @Override
    public boolean isEnabled() {
        return PokeAlertClient.getInstance().config.telegramEnabled && config != null && config.isValid();
    }

    @Override
    public String getServiceName() {
        return "Telegram Notification";
    }

    @Override
    public void shutdown() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }
    
    /**
     * Send egg timer notification to Telegram
     */
    public void sendEggTimerNotification(String message) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            JsonObject jsonPayload = new JsonObject();
            jsonPayload.addProperty("chat_id", config.getChatId());
            jsonPayload.addProperty("text", message);
            jsonPayload.addProperty("parse_mode", "HTML");
            
            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                jsonPayload.toString()
            );
            
            String url = String.format("https://api.telegram.org/bot%s/sendMessage", config.getBotToken());
            Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    PokeAlertClient.LOGGER.error("Failed to send egg timer notification to Telegram: {}", e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        PokeAlertClient.LOGGER.debug("Egg timer notification sent to Telegram successfully");
                    } else {
                        PokeAlertClient.LOGGER.error(
                            "Telegram API returned error for egg timer: {} - {}",
                            response.code(),
                            response.body() != null ? response.body().string() : "No body"
                        );
                    }
                    response.close();
                }
            });
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("Error sending egg timer notification to Telegram", e);
        }
    }

    /**
     * Format a rich message for Telegram
     */
    private String formatMessage(PokemonSpawnData data) {
        String time = TIME_FORMATTER.format(Instant.ofEpochMilli(data.getTimestamp()));
        
        // Get Pokemon name without "Shiny" prefix for URL
        String pokemonName = data.getPokemonName();
        
        // Build display name with Shiny prefix if applicable
        String displayName = data.isShiny() ? "Shiny " + pokemonName : pokemonName;
        
        // Get Bulbapedia URL and rarity from shared scraper (uses cached value from PokemonSpawnData)
        String bulbapediaUrl = data.getBulbapediaUrl();
        String rarity = data.getRarityText();
        
        // Clean list format with formatted values and hyperlinked Pokemon name
        StringBuilder message = new StringBuilder();
        message.append("<b>🚨 PokéAlert</b>\n");
        message.append("• Pokémon: <a href=\"").append(bulbapediaUrl).append("\">")
               .append(displayName).append("</a>\n");
        message.append("• Rarity: <i>").append(rarity).append("</i>\n");
        message.append("• Detected: <code>").append(time).append("</code>\n");
        message.append("• Position: <code>X:").append(data.getX())
               .append(" Y:").append(data.getY())
               .append(" Z:").append(data.getZ()).append("</code>\n");
        message.append("• Location: <i>").append(data.getWorldName()).append("</i>");
        
        return message.toString();
    }

    /**
     * Escape JSON special characters
     */
    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * Check if we're within rate limits
     */
    private boolean checkRateLimit() {
        long now = System.currentTimeMillis();
        
        // Reset counter every minute
        if (now - lastResetTime > 60000) {
            notificationCount = 0;
            lastResetTime = now;
        }

        // Check if we've exceeded the limit
        if (notificationCount >= config.getMaxNotificationsPerMinute()) {
            return false;
        }

        notificationCount++;
        return true;
    }
}

