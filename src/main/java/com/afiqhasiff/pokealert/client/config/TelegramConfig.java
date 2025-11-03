package com.afiqhasiff.pokealert.client.config;

/**
 * Configuration for Telegram notifications.
 * This config is stored separately from user preferences because it contains sensitive data.
 * File location: config/pokealert-telegram.json
 */
public class TelegramConfig {
    private boolean enabled = false;
    private String botToken = "";
    private String chatId = "";
    private String apiUrl = "https://api.telegram.org";
    
    // Rate limiting settings
    private int maxNotificationsPerMinute = 10;
    private int cooldownSeconds = 30;

    // Getters and setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public int getMaxNotificationsPerMinute() {
        return maxNotificationsPerMinute;
    }

    public void setMaxNotificationsPerMinute(int maxNotificationsPerMinute) {
        this.maxNotificationsPerMinute = maxNotificationsPerMinute;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    /**
     * Check if the configuration is valid for sending notifications
     */
    public boolean isValid() {
        return enabled 
            && botToken != null && !botToken.trim().isEmpty() 
            && chatId != null && !chatId.trim().isEmpty();
    }

    /**
     * Get the full API URL for sending messages
     */
    public String getSendMessageUrl() {
        return String.format("%s/bot%s/sendMessage", apiUrl, botToken);
    }
}

