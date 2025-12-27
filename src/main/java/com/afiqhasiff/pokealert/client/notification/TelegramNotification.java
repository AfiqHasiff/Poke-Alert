package com.afiqhasiff.pokealert.client.notification;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.config.PokeAlertConfig;
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
    private PokeAlertConfig config;
    private OkHttpClient httpClient;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault());

    // Rate limiting
    private int notificationCount = 0;
    private long lastResetTime = System.currentTimeMillis();

    @Override
    public void initialize() {
        config = PokeAlertClient.getInstance().config;
        
        // Create HTTP client with reasonable timeouts
        httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

        if (config.isTelegramValid()) {
            PokeAlertClient.LOGGER.info("Telegram notification service initialized");
        } else {
            PokeAlertClient.LOGGER.warn("Telegram notification service initialized but not configured");
        }
    }

    @Override
    public void sendNotification(PokemonSpawnData data) {
        // Refresh config reference
        config = PokeAlertClient.getInstance().config;
        
        // Check if Telegram is enabled and configured
        if (!config.telegramEnabled || !config.isTelegramValid()) {
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
            escapeJson(config.telegramChatId),
            escapeJson(message)
        );

        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request request = new Request.Builder()
            .url(config.getTelegramSendMessageUrl())
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
        config = PokeAlertClient.getInstance().config;
        return config.telegramEnabled && config.isTelegramValid();
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
        // Refresh config reference
        config = PokeAlertClient.getInstance().config;
        
        if (!isEnabled()) {
            PokeAlertClient.LOGGER.debug("Egg timer Telegram notification skipped - service not enabled or config invalid");
            return;
        }
        
        try {
            JsonObject jsonPayload = new JsonObject();
            jsonPayload.addProperty("chat_id", config.telegramChatId);
            jsonPayload.addProperty("text", message);
            jsonPayload.addProperty("parse_mode", "HTML");
            
            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                jsonPayload.toString()
            );
            
            Request request = new Request.Builder()
                .url(config.getTelegramSendMessageUrl())
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
     * Send DM notification to Telegram
     */
    public void sendDmNotification(String sender, String message, boolean isAvoided) {
        // Refresh config reference
        config = PokeAlertClient.getInstance().config;
        
        if (!isEnabled()) {
            PokeAlertClient.LOGGER.debug("DM Telegram notification skipped - service not enabled or config invalid");
            return;
        }
        
        // Check rate limiting (uses existing Telegram rate limiter)
        if (!checkRateLimit()) {
            PokeAlertClient.LOGGER.warn("Telegram rate limit exceeded, skipping DM notification");
            return;
        }
        
        try {
            // Format message
            StringBuilder messageText = new StringBuilder();
            messageText.append("<b>💬 Egg Hatcher DM</b>\n");
            messageText.append("From: <code>").append(escapeHtml(sender)).append("</code>\n");
            messageText.append("Message: ").append(escapeHtml(message));
            
            if (isAvoided) {
                messageText.append("\n⚠️ <b>WARNING:</b> This player is in your avoided list");
            }
            
            // Add reply hint if DM replies are enabled
            if (config.dmReplyEnabled) {
                messageText.append("\n\n💬 <i>Reply to this message to respond</i>");
            }
            
            JsonObject jsonPayload = new JsonObject();
            jsonPayload.addProperty("chat_id", config.telegramChatId);
            jsonPayload.addProperty("text", messageText.toString());
            jsonPayload.addProperty("parse_mode", "HTML");
            
            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                jsonPayload.toString()
            );
            
            Request request = new Request.Builder()
                .url(config.getTelegramSendMessageUrl())
                .post(body)
                .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    PokeAlertClient.LOGGER.error("Failed to send DM notification to Telegram: {}", e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        PokeAlertClient.LOGGER.debug("DM notification sent to Telegram successfully");
                    } else {
                        PokeAlertClient.LOGGER.error(
                            "Telegram API returned error for DM: {} - {}",
                            response.code(),
                            response.body() != null ? response.body().string() : "No body"
                        );
                    }
                    response.close();
                }
            });
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("Error sending DM notification to Telegram", e);
        }
    }
    
    /**
     * Escape HTML special characters for Telegram
     */
    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
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
        if (notificationCount >= config.telegramMaxNotificationsPerMinute) {
            return false;
        }

        notificationCount++;
        return true;
    }
}

