package com.afiqhasiff.pokealert.client.telegram;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.config.ConfigManager;
import com.afiqhasiff.pokealert.client.config.PokeAlertConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Receives and processes Telegram commands via long polling.
 * Executes commands on Minecraft client thread and sends feedback back to Telegram.
 */
public class TelegramCommandReceiver {
    private static TelegramCommandReceiver instance;
    private OkHttpClient httpClient;
    private PokeAlertConfig config;
    private int lastUpdateId = 0;
    private boolean isPolling = false;
    private Thread pollingThread;
    
    // Rate limiting: Track last command execution time per user
    private long lastCommandTime = 0;
    private static final long RATE_LIMIT_WINDOW_MS = 5000; // 5 seconds
    
    private TelegramCommandReceiver() {
        httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS) // Long polling timeout (30s + buffer)
            .build();
    }
    
    public static TelegramCommandReceiver getInstance() {
        if (instance == null) {
            instance = new TelegramCommandReceiver();
        }
        return instance;
    }
    
    /**
     * Start polling for Telegram updates
     */
    public void startPolling() {
        if (isPolling) {
            PokeAlertClient.LOGGER.warn("TelegramCommandReceiver: Already polling");
            return;
        }
        
        config = ConfigManager.getConfig();
        
        if (!config.telegramCommandExecutionEnabled) {
            PokeAlertClient.LOGGER.info("TelegramCommandReceiver: Command execution disabled");
            return;
        }
        
        if (!config.isTelegramValid()) {
            PokeAlertClient.LOGGER.warn("TelegramCommandReceiver: Telegram not configured (missing token or chat ID)");
            return;
        }
        
        isPolling = true;
        pollingThread = new Thread(this::pollingLoop, "TelegramCommandReceiver");
        pollingThread.setDaemon(true);
        pollingThread.start();
        
        PokeAlertClient.LOGGER.info("TelegramCommandReceiver: Started polling (interval: {}ms)", config.telegramPollingInterval);
    }
    
    /**
     * Stop polling for Telegram updates
     */
    public void stopPolling() {
        if (!isPolling) {
            return;
        }
        
        isPolling = false;
        
        if (pollingThread != null && pollingThread.isAlive()) {
            pollingThread.interrupt();
            try {
                pollingThread.join(2000);
            } catch (InterruptedException e) {
                PokeAlertClient.LOGGER.warn("TelegramCommandReceiver: Interrupted while stopping polling thread");
            }
        }
        
        PokeAlertClient.LOGGER.info("TelegramCommandReceiver: Stopped polling");
    }
    
    /**
     * Main polling loop
     */
    private void pollingLoop() {
        PokeAlertClient.LOGGER.info("TelegramCommandReceiver: Polling loop started");
        
        while (isPolling) {
            try {
                // Refresh config
                config = ConfigManager.getConfig();
                
                // Check if still enabled
                if (!config.telegramCommandExecutionEnabled || !config.isTelegramValid()) {
                    PokeAlertClient.LOGGER.info("TelegramCommandReceiver: Disabled or invalid config, stopping");
                    isPolling = false;
                    break;
                }
                
                // Poll for updates
                pollUpdates();
                
                // Wait before next poll
                Thread.sleep(config.telegramPollingInterval);
                
            } catch (InterruptedException e) {
                PokeAlertClient.LOGGER.info("TelegramCommandReceiver: Polling thread interrupted");
                break;
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("TelegramCommandReceiver: Error in polling loop", e);
                // Continue polling after error
                try {
                    Thread.sleep(5000); // Wait 5 seconds before retry
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
        
        PokeAlertClient.LOGGER.info("TelegramCommandReceiver: Polling loop ended");
    }
    
    /**
     * Poll Telegram API for updates
     */
    private void pollUpdates() {
        try {
            String url = String.format(
                "%s/bot%s/getUpdates?offset=%d&timeout=30&allowed_updates=[\"message\"]",
                config.telegramApiUrl,
                config.telegramBotToken,
                lastUpdateId + 1
            );
            
            Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    PokeAlertClient.LOGGER.warn("TelegramCommandReceiver: API error {} - {}", 
                        response.code(), response.body() != null ? response.body().string() : "No body");
                    return;
                }
                
                String responseBody = response.body() != null ? response.body().string() : "{}";
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                
                if (!jsonResponse.get("ok").getAsBoolean()) {
                    PokeAlertClient.LOGGER.warn("TelegramCommandReceiver: API returned error: {}", 
                        jsonResponse.has("description") ? jsonResponse.get("description").getAsString() : "Unknown error");
                    return;
                }
                
                JsonArray updates = jsonResponse.getAsJsonArray("result");
                if (updates == null || updates.size() == 0) {
                    return; // No updates
                }
                
                // Process each update
                for (int i = 0; i < updates.size(); i++) {
                    JsonObject update = updates.get(i).getAsJsonObject();
                    processUpdate(update);
                }
                
            }
        } catch (IOException e) {
            PokeAlertClient.LOGGER.error("TelegramCommandReceiver: Network error polling updates", e);
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("TelegramCommandReceiver: Error polling updates", e);
        }
    }
    
    /**
     * Process a single Telegram update
     */
    private void processUpdate(JsonObject update) {
        try {
            int updateId = update.get("update_id").getAsInt();
            lastUpdateId = Math.max(lastUpdateId, updateId);
            
            // Only process messages
            if (!update.has("message")) {
                return;
            }
            
            JsonObject message = update.getAsJsonObject("message");
            
            // Only process text messages
            if (!message.has("text")) {
                return;
            }
            
            String text = message.get("text").getAsString();
            
            // Extract sender info first (needed for DM reply handling)
            if (!message.has("from")) {
                return;
            }
            
            JsonObject from = message.getAsJsonObject("from");
            long userId = from.get("id").getAsLong();
            JsonObject chat = message.get("chat").getAsJsonObject();
            long chatId = chat.get("id").getAsLong();
            int messageId = message.get("message_id").getAsInt();
            
            // Check if this is a reply to a DM notification
            if (message.has("reply_to_message")) {
                JsonObject replyTo = message.getAsJsonObject("reply_to_message");
                if (replyTo.has("text")) {
                    String repliedText = replyTo.get("text").getAsString();
                    // Check if this is a DM notification (contains "Egg Hatcher DM")
                    if (repliedText.contains("💬 Egg Hatcher DM") || repliedText.contains("Egg Hatcher DM")) {
                        // This is a reply to a DM notification
                        handleDmReply(text, repliedText, chatId, messageId, userId);
                        return; // Don't process as regular command
                    }
                }
            }
            
            // Parse Telegram command format
            // Supports: /pa help, /pahelp, /pahelp@botname
            String commandPrefix = "/pa";
            String commandText = null;
            
            // Check if it's a /pa command (with or without space, with or without bot mention)
            if (text.startsWith(commandPrefix)) {
                // Remove bot mention if present (e.g., /pahelp@CobblemonFinder_bot)
                String textWithoutBotMention = text.split("@")[0];
                
                // Check if command has space (e.g., "/pa help") or no space (e.g., "/pahelp")
                if (textWithoutBotMention.length() == commandPrefix.length()) {
                    // Just "/pa" - treat as help command
                    commandText = "help";
                } else if (textWithoutBotMention.startsWith(commandPrefix + " ")) {
                    // Format: "/pa help" (with space)
                    commandText = textWithoutBotMention.substring(commandPrefix.length() + 1).trim();
                } else if (textWithoutBotMention.startsWith(commandPrefix)) {
                    // Format: "/pahelp" (no space)
                    commandText = textWithoutBotMention.substring(commandPrefix.length()).trim();
                }
            }
            
            // If not a valid /pa command, ignore
            if (commandText == null) {
                return;
            }
            
            // Check authorization
            if (!isAuthorizedUser(userId)) {
                PokeAlertClient.LOGGER.warn("TelegramCommandReceiver: Unauthorized user {} attempted command: {}", userId, text);
                sendTelegramResponse(chatId, "❌ Unauthorized: You are not authorized to execute commands", messageId);
                return;
            }
            
            // Check rate limit
            if (!checkRateLimit()) {
                PokeAlertClient.LOGGER.warn("TelegramCommandReceiver: Rate limit exceeded for user {}", userId);
                sendTelegramResponse(chatId, "⏳ Rate limit: Please wait before sending another command", messageId);
                return;
            }
            
            // Execute command
            executeCommand(commandText, chatId, messageId);
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("TelegramCommandReceiver: Error processing update", e);
        }
    }
    
    /**
     * Check if user is authorized
     */
    private boolean isAuthorizedUser(long userId) {
        if (config.telegramAuthorizedUsers == null || config.telegramAuthorizedUsers.length == 0) {
            return false; // No users authorized
        }
        return Arrays.stream(config.telegramAuthorizedUsers)
            .anyMatch(id -> id == userId);
    }
    
    /**
     * Check rate limit (1 command per 5 seconds)
     */
    private boolean checkRateLimit() {
        long now = System.currentTimeMillis();
        long timeSinceLastCommand = now - lastCommandTime;
        
        if (timeSinceLastCommand < RATE_LIMIT_WINDOW_MS) {
            return false; // Rate limit exceeded
        }
        
        lastCommandTime = now;
        return true;
    }
    
    /**
     * Handle reply to a DM notification
     */
    private void handleDmReply(String replyText, String originalNotification, long chatId, int messageId, long userId) {
        // Refresh config
        config = ConfigManager.getConfig();
        
        // Check if DM replies are enabled
        if (!config.dmReplyEnabled) {
            sendTelegramResponse(chatId, "❌ DM replies are disabled", messageId);
            return;
        }
        
        // Check authorization
        if (!isAuthorizedUser(userId)) {
            PokeAlertClient.LOGGER.warn("TelegramCommandReceiver: Unauthorized user {} attempted DM reply", userId);
            sendTelegramResponse(chatId, "❌ Unauthorized: You are not authorized to reply to DMs", messageId);
            return;
        }
        
        // Check rate limit
        if (!checkRateLimit()) {
            PokeAlertClient.LOGGER.warn("TelegramCommandReceiver: Rate limit exceeded for DM reply from user {}", userId);
            sendTelegramResponse(chatId, "⏳ Rate limit: Please wait before sending another reply", messageId);
            return;
        }
        
        // Validate reply text
        if (replyText == null || replyText.trim().isEmpty()) {
            sendTelegramResponse(chatId, "❌ Reply message cannot be empty", messageId);
            return;
        }
        
        // Parse sender from original notification
        // Format: "From: <code>sender</code>"
        String sender = extractSenderFromNotification(originalNotification);
        if (sender == null || sender.isEmpty()) {
            PokeAlertClient.LOGGER.warn("TelegramCommandReceiver: Could not extract sender from DM notification");
            sendTelegramResponse(chatId, "❌ Could not determine sender. Please reply directly to the DM notification.", messageId);
            return;
        }
        
        // Check if sender is in avoided list
        if (isPlayerAvoided(sender, config)) {
            PokeAlertClient.LOGGER.warn("TelegramCommandReceiver: Attempted reply to avoided player: {}", sender);
            sendTelegramResponse(chatId, 
                "⚠️ Warning: This player is in your avoided list. Reply not sent.", 
                messageId);
            return;
        }
        
        // Send in-game DM command
        sendInGameDm(sender, replyText.trim());
        
        // Send confirmation to Telegram
        sendTelegramResponse(chatId, 
            "✅ Reply sent to <code>" + escapeHtml(sender) + "</code>: " + escapeHtml(replyText.trim()), 
            messageId);
        
        PokeAlertClient.LOGGER.info("TelegramCommandReceiver: DM reply sent to {}: {}", sender, replyText);
    }
    
    /**
     * Extract sender name from DM notification message
     * Format: "From: <code>sender</code>"
     */
    private String extractSenderFromNotification(String notification) {
        if (notification == null || notification.isEmpty()) {
            return null;
        }
        
        // Look for "From: <code>sender</code>" pattern
        // Handle both HTML and plain text formats
        String[] lines = notification.split("\n");
        for (String line : lines) {
            if (line.contains("From:")) {
                // Try to extract from HTML format: "From: <code>sender</code>"
                int codeStart = line.indexOf("<code>");
                int codeEnd = line.indexOf("</code>");
                if (codeStart != -1 && codeEnd != -1 && codeEnd > codeStart) {
                    return line.substring(codeStart + 6, codeEnd).trim();
                }
                
                // Try plain text format: "From: sender"
                int colonIndex = line.indexOf(":");
                if (colonIndex != -1 && colonIndex < line.length() - 1) {
                    String afterColon = line.substring(colonIndex + 1).trim();
                    // Remove any HTML tags if present
                    afterColon = afterColon.replaceAll("<[^>]+>", "").trim();
                    if (!afterColon.isEmpty()) {
                        return afterColon;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Check if player is in avoided list
     */
    private boolean isPlayerAvoided(String playerName, PokeAlertConfig config) {
        if (config.playersToAvoid == null || config.playersToAvoid.length == 0) {
            return false;
        }
        
        String playerLower = playerName.toLowerCase();
        return Arrays.stream(config.playersToAvoid)
            .anyMatch(avoided -> avoided != null && avoided.toLowerCase().equals(playerLower));
    }
    
    /**
     * Send in-game DM command
     */
    private void sendInGameDm(String recipient, String message) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        
        if (client == null || client.player == null) {
            PokeAlertClient.LOGGER.warn("TelegramCommandReceiver: Cannot send DM - client or player is null");
            return;
        }
        
        // Get DM command format from config
        String dmCommand = config.dmCommandFormat;
        if (dmCommand == null || dmCommand.trim().isEmpty()) {
            dmCommand = "/dm"; // Fallback to default
        }
        
        // Ensure command starts with /
        if (!dmCommand.startsWith("/")) {
            dmCommand = "/" + dmCommand;
        }
        
        // Format command: /dm <recipient> <message>
        // Handle player names with spaces by quoting if needed
        String command;
        if (recipient.contains(" ")) {
            command = String.format("%s \"%s\" %s", dmCommand, recipient, message);
        } else {
            command = String.format("%s %s %s", dmCommand, recipient, message);
        }
        
        // Send command on client thread
        client.execute(() -> {
            if (client.player != null && client.player.networkHandler != null) {
                // Remove leading '/' if present (sendChatCommand expects command without '/')
                String commandToSend = command.startsWith("/") ? command.substring(1) : command;
                client.player.networkHandler.sendChatCommand(commandToSend);
                
                PokeAlertClient.LOGGER.info("TelegramCommandReceiver: Sent in-game DM command: {}", command);
            }
        });
    }
    
    /**
     * Escape HTML special characters
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    /**
     * Execute a command from Telegram
     * @param commandText The command without /pa prefix (e.g., "help", "status", "realm toggle")
     */
    private void executeCommand(String commandText, long chatId, int messageId) {
        PokeAlertClient.LOGGER.info("TelegramCommandReceiver: Executing command: {}", commandText);
        
        // Command is already parsed (without /pa prefix)
        String command = commandText.trim().toLowerCase();
        
        // Handle empty command (just "/pa")
        if (command.isEmpty()) {
            command = "help";
        }
        
        // For now, send a placeholder response
        // TODO: Implement command execution logic
        sendTelegramResponse(chatId, "⚠️ Command execution not yet implemented: " + command, messageId);
    }
    
    /**
     * Send response back to Telegram
     */
    private void sendTelegramResponse(long chatId, String message, int replyToMessageId) {
        try {
            config = ConfigManager.getConfig(); // Refresh config
            
            JsonObject jsonPayload = new JsonObject();
            jsonPayload.addProperty("chat_id", String.valueOf(chatId));
            jsonPayload.addProperty("text", message);
            jsonPayload.addProperty("reply_to_message_id", replyToMessageId);
            jsonPayload.addProperty("parse_mode", "HTML");
            
            String url = String.format(
                "%s/bot%s/sendMessage",
                config.telegramApiUrl,
                config.telegramBotToken
            );
            
            RequestBody body = RequestBody.create(
                jsonPayload.toString(),
                MediaType.parse("application/json")
            );
            
            Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
            
            // Send async to avoid blocking
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    PokeAlertClient.LOGGER.error("TelegramCommandReceiver: Failed to send response", e);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        PokeAlertClient.LOGGER.warn("TelegramCommandReceiver: Failed to send response: {} - {}", 
                            response.code(), response.body() != null ? response.body().string() : "No body");
                    }
                    response.close();
                }
            });
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("TelegramCommandReceiver: Error sending response", e);
        }
    }
    
    /**
     * Reload configuration
     */
    public void reloadConfig() {
        config = ConfigManager.getConfig();
    }
    
    /**
     * Check if polling is active
     */
    public boolean isPolling() {
        return isPolling;
    }
}

