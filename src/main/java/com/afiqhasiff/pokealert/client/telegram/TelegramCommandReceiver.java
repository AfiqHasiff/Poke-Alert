package com.afiqhasiff.pokealert.client.telegram;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.config.ConfigManager;
import com.afiqhasiff.pokealert.client.config.PokeAlertConfig;
import com.afiqhasiff.pokealert.client.util.PokemonLists;
import com.afiqhasiff.pokealert.client.notification.EggTimerManager;
import com.afiqhasiff.pokealert.client.automation.EggHatcher;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    
    // Bot username (fetched on initialization)
    private String botUsername = null;
    
    // Rate limiting: Track last command execution time per user (fixed: per-user instead of global)
    private Map<Long, Long> lastCommandTimePerUser = new HashMap<>();
    private static final long RATE_LIMIT_WINDOW_MS = 1000; // 1 second
    
    // Track when polling started to skip old commands
    private long pollingStartTime = 0;
    private boolean readyToProcessCommands = false;
    
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
        pollingStartTime = System.currentTimeMillis();
        readyToProcessCommands = false; // Don't process commands until we've initialized
        
        pollingThread = new Thread(this::pollingLoop, "TelegramCommandReceiver");
        pollingThread.setDaemon(true);
        pollingThread.start();
        
        // Fetch bot username on initialization
        initializeBotInfo();
        
        // Mark as ready after a short delay to ensure we skip old commands
        // Old commands will be skipped because their update_id will be acknowledged without processing
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Wait 2 seconds before processing commands
                readyToProcessCommands = true;
                PokeAlertClient.LOGGER.info("TelegramCommandReceiver: Ready to process commands (startup delay complete)");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "TelegramCommandReceiver-Init").start();
        
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
            // Build URL with proper query parameters
            String url = String.format(
                "%s/bot%s/getUpdates?offset=%d&timeout=30&allowed_updates=[\"message\"]",
                config.telegramApiUrl,
                config.telegramBotToken,
                lastUpdateId + 1
            );
            
            PokeAlertClient.LOGGER.debug("TelegramCommandReceiver: Polling for updates (offset: {})", lastUpdateId + 1);
            
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
                
                PokeAlertClient.LOGGER.debug("TelegramCommandReceiver: Received {} update(s)", updates.size());
                
                // Process each update
                for (int i = 0; i < updates.size(); i++) {
                    JsonObject update = updates.get(i).getAsJsonObject();
                    int updateId = update.get("update_id").getAsInt();
                    
                    // Always acknowledge updates (update lastUpdateId) to prevent re-processing
                    lastUpdateId = Math.max(lastUpdateId, updateId);
                    
                    // Only process commands if we're ready (skip old commands on startup)
                    if (readyToProcessCommands) {
                        PokeAlertClient.LOGGER.debug("TelegramCommandReceiver: Processing update ID: {}", updateId);
                        processUpdate(update);
                    } else {
                        PokeAlertClient.LOGGER.debug("TelegramCommandReceiver: Skipping update ID: {} (startup delay, readyToProcessCommands: {})", 
                            updateId, readyToProcessCommands);
                        // Update is acknowledged but not processed - prevents rate limiting from old commands
                    }
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
            // Note: updateId is already acknowledged in pollUpdates() before calling processUpdate
            int updateId = update.get("update_id").getAsInt();
            
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
            
            // Detect chat type: negative ID = group, positive ID = private chat
            boolean isGroupChat = chatId < 0;
            PokeAlertClient.LOGGER.debug("TelegramCommandReceiver: Chat type - Group: {}, Chat ID: {}", isGroupChat, chatId);
            
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
            
            // Parse Telegram command format with bot mention verification
            String commandText = parseCommand(text, isGroupChat, chatId, messageId);
            
            // If command parsing failed or was rejected, return early
            if (commandText == null) {
                return;
            }
            
            PokeAlertClient.LOGGER.info("TelegramCommandReceiver: Parsed command '{}' from text '{}' (Group: {})", commandText, text, isGroupChat);
            
            // Check authorization
            if (!isAuthorizedUser(userId)) {
                PokeAlertClient.LOGGER.warn("TelegramCommandReceiver: Unauthorized user {} attempted command: {}", userId, text);
                sendTelegramResponse(chatId, "❌ Unauthorized: You are not authorized to execute commands", messageId);
                return;
            }
            
            // Check rate limit (per-user)
            if (!checkRateLimit(userId)) {
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
     * Initialize bot info (fetch username)
     */
    private void initializeBotInfo() {
        try {
            String url = String.format(
                "%s/bot%s/getMe",
                config.telegramApiUrl,
                config.telegramBotToken
            );
            
            Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    PokeAlertClient.LOGGER.warn("TelegramCommandReceiver: Failed to get bot info: {} - {}", 
                        response.code(), response.body() != null ? response.body().string() : "No body");
                    return;
                }
                
                String responseBody = response.body() != null ? response.body().string() : "{}";
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                
                if (jsonResponse.get("ok").getAsBoolean()) {
                    JsonObject result = jsonResponse.getAsJsonObject("result");
                    botUsername = result.get("username").getAsString();
                    PokeAlertClient.LOGGER.info("TelegramCommandReceiver: Bot username initialized: @{}", botUsername);
                } else {
                    PokeAlertClient.LOGGER.warn("TelegramCommandReceiver: Failed to get bot username: {}", 
                        jsonResponse.has("description") ? jsonResponse.get("description").getAsString() : "Unknown error");
                }
            }
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("TelegramCommandReceiver: Error initializing bot info", e);
        }
    }
    
    /**
     * Parse command with bot mention verification
     * @param text The message text
     * @param isGroupChat Whether this is a group chat
     * @param chatId The chat ID (for error messages)
     * @param messageId The message ID (for error messages)
     * @return The parsed command text, or null if invalid/rejected
     */
    private String parseCommand(String text, boolean isGroupChat, long chatId, int messageId) {
        String commandPrefix = "/pa";
        
        // Check if it's a /pa command
        if (!text.startsWith(commandPrefix)) {
            return null;
        }
        
        PokeAlertClient.LOGGER.debug("TelegramCommandReceiver: Processing message text: '{}'", text);
        
        // Extract bot mention if present
        boolean hasBotMention = text.contains("@");
        String mentionedBot = null;
        String textWithoutBotMention = text;
        
        if (hasBotMention) {
            String[] parts = text.split("@", 2);
            if (parts.length == 2 && !parts[1].isEmpty()) {
                textWithoutBotMention = parts[0];
                // Extract bot name (before space or end of string)
                mentionedBot = parts[1].split("\\s+")[0];
                PokeAlertClient.LOGGER.debug("TelegramCommandReceiver: Bot mention detected: @{}", mentionedBot);
            }
        }
        
        // In groups, bot mention is REQUIRED (Privacy Mode)
        if (isGroupChat && !hasBotMention) {
            PokeAlertClient.LOGGER.debug("TelegramCommandReceiver: Group command missing bot mention: {}", text);
            sendTelegramResponse(chatId, 
                "⚠️ In group chats, commands must mention the bot.\n" +
                "Use: /pahelp@" + (botUsername != null ? botUsername : "botname") + "\n" +
                "Or disable Privacy Mode in @BotFather", 
                messageId);
            return null;
        }
        
        // Verify bot mention matches our bot (if present)
        if (hasBotMention && botUsername != null) {
            if (!mentionedBot.equalsIgnoreCase(botUsername)) {
                PokeAlertClient.LOGGER.debug("TelegramCommandReceiver: Command mentions wrong bot: @{} (expected: @{})", 
                    mentionedBot, botUsername);
                // Don't send error - this command is for another bot
                return null;
            }
        }
        
        // Parse command text
        String commandText = null;
        
        if (textWithoutBotMention.length() == commandPrefix.length()) {
            // Just "/pa" - treat as help command
            commandText = "help";
            PokeAlertClient.LOGGER.debug("TelegramCommandReceiver: Detected '/pa' command, defaulting to 'help'");
        } else if (textWithoutBotMention.startsWith(commandPrefix + " ")) {
            // Format: "/pa help" (with space)
            commandText = textWithoutBotMention.substring(commandPrefix.length() + 1).trim();
            PokeAlertClient.LOGGER.debug("TelegramCommandReceiver: Detected '/pa <command>' format, command: '{}'", commandText);
        } else if (textWithoutBotMention.startsWith(commandPrefix)) {
            // Format: "/pahelp" (no space)
            commandText = textWithoutBotMention.substring(commandPrefix.length()).trim();
            PokeAlertClient.LOGGER.debug("TelegramCommandReceiver: Detected '/pa<command>' format, command: '{}'", commandText);
        }
        
        return commandText;
    }
    
    /**
     * Check rate limit per user (1 command per 1 second per user)
     */
    private boolean checkRateLimit(long userId) {
        long now = System.currentTimeMillis();
        Long lastCommandTime = lastCommandTimePerUser.get(userId);
        
        if (lastCommandTime != null) {
            long timeSinceLastCommand = now - lastCommandTime;
            if (timeSinceLastCommand < RATE_LIMIT_WINDOW_MS) {
                return false; // Rate limit exceeded for this user
            }
        }
        
        // Update last command time for this user
        lastCommandTimePerUser.put(userId, now);
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
        
        // Check rate limit (per-user)
        if (!checkRateLimit(userId)) {
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
        String command = commandText.trim();
        
        // Handle empty command (just "/pa")
        if (command.isEmpty()) {
            command = "help";
        }
        
        // Make final for lambda
        final String finalCommand = command;
        final long finalChatId = chatId;
        final int finalMessageId = messageId;
        
        // Execute on client thread to ensure thread safety
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null) {
            sendTelegramResponse(chatId, "❌ Error: Minecraft client not available", messageId);
            return;
        }
        
        // Execute command on client thread
        client.execute(() -> {
            try {
                String response = executeCommandInternal(finalCommand);
                sendTelegramResponse(finalChatId, response, finalMessageId);
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("TelegramCommandReceiver: Error executing command: {}", finalCommand, e);
                sendTelegramResponse(finalChatId, "❌ Error executing command: " + e.getMessage(), finalMessageId);
            }
        });
    }
    
    /**
     * Internal command execution logic
     * Returns formatted response string for Telegram
     */
    private String executeCommandInternal(String command) {
        String[] parts = command.trim().split("\\s+");
        if (parts.length == 0) {
            return executeHelp();
        }
        
        String cmd = parts[0].toLowerCase();
        
        // Basic commands
        switch (cmd) {
            case "help":
            case "h":
                return executeHelp();
            case "status":
            case "stat":
                return executeStatus();
            case "enable":
            case "on":
                return executeEnable(true);
            case "disable":
            case "off":
                return executeEnable(false);
        }
        
        // Multi-part commands
        if (parts.length >= 2) {
            String subCmd = parts[1].toLowerCase();
            
            // Categories
            if (cmd.equals("categories") || cmd.equals("cat")) {
                if (parts.length >= 3) {
                    boolean enable = parts[2].equalsIgnoreCase("enable") || parts[2].equalsIgnoreCase("on");
                    return executeCategory(parts[1], enable);
                }
            }
            
            // Notifications
            if (cmd.equals("notifications") || cmd.equals("notif")) {
                if (parts.length >= 3) {
                    boolean enable = parts[2].equalsIgnoreCase("enable") || parts[2].equalsIgnoreCase("on");
                    return executeNotification(parts[1], enable);
                }
            }
            
            // List management
            if (cmd.equals("list") || cmd.equals("ls")) {
                return executeList(parts[1]);
            }
            
            // Whitelist
            if (cmd.equals("whitelist") || cmd.equals("wl")) {
                if (parts.length >= 3) {
                    if (subCmd.equals("add")) {
                        return executeWhitelistAdd(parts[2]);
                    } else if (subCmd.equals("remove") || subCmd.equals("rm")) {
                        return executeWhitelistRemove(parts[2]);
                    }
                } else if (subCmd.equals("list") || subCmd.equals("ls")) {
                    return executeWhitelistList();
                }
            }
            
            // Blacklist
            if (cmd.equals("blacklist") || cmd.equals("bl")) {
                if (parts.length >= 3) {
                    if (subCmd.equals("add")) {
                        return executeBlacklistAdd(parts[2]);
                    } else if (subCmd.equals("remove") || subCmd.equals("rm")) {
                        return executeBlacklistRemove(parts[2]);
                    }
                } else if (subCmd.equals("list") || subCmd.equals("ls")) {
                    return executeBlacklistList();
                }
            }
            
            // Egg timer
            if (cmd.equals("eggtimer") || cmd.equals("timer") || cmd.equals("et")) {
                if (subCmd.equals("start")) {
                    if (parts.length >= 3) {
                        try {
                            int minutes = Integer.parseInt(parts[2]);
                            return executeEggTimerStart(minutes);
                        } catch (NumberFormatException e) {
                            return "❌ Invalid minutes: " + parts[2];
                        }
                    }
                    return executeEggTimerStart(-1);
                } else if (subCmd.equals("stop")) {
                    return executeEggTimerStop();
                } else if (subCmd.equals("status") || subCmd.equals("stat")) {
                    return executeEggTimerStatus();
                } else if (subCmd.equals("duration")) {
                    if (parts.length >= 3) {
                        try {
                            int minutes = Integer.parseInt(parts[2]);
                            return executeEggTimerDuration(minutes);
                        } catch (NumberFormatException e) {
                            return "❌ Invalid minutes: " + parts[2];
                        }
                    }
                }
            }
            
            // Realm/Egg Hatcher
            if (cmd.equals("realm") || cmd.equals("hatcher")) {
                if (subCmd.equals("toggle") || subCmd.equals("t")) {
                    return executeRealmToggle();
                } else if (subCmd.equals("status") || subCmd.equals("stat")) {
                    return executeRealmStatus();
                }
            }
        }
        
        return "❌ Unknown command: " + command + "\nUse /pahelp to see available commands";
    }
    
    // Command implementations
    
    private String executeHelp() {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>PokéAlert v3.0.0</b> - Pokémon Detection Mod\n\n");
        sb.append("<b>Basic Commands:</b>\n");
        sb.append("/pahelp - Show this help\n");
        sb.append("/pastatus - Show current status\n");
        sb.append("/pa enable - Enable the mod\n");
        sb.append("/pa disable - Disable the mod\n\n");
        
        sb.append("<b>Categories:</b>\n");
        sb.append("/pa categories &lt;type&gt; &lt;enable|disable&gt;\n");
        sb.append("Types: legendaries, mythics, shinies, starters, babies, ultrabeasts, paradox\n\n");
        
        sb.append("<b>Lists:</b>\n");
        sb.append("/pa list &lt;type&gt; - View Pokémon lists\n");
        sb.append("/pa whitelist add/remove/list &lt;pokemon&gt;\n");
        sb.append("/pa blacklist add/remove/list &lt;pokemon&gt;\n\n");
        
        sb.append("<b>Notifications:</b>\n");
        sb.append("/pa notifications &lt;text|sound|telegram&gt; &lt;enable|disable&gt;\n\n");
        
        sb.append("<b>Egg Timer:</b>\n");
        sb.append("/pa eggtimer start [minutes]\n");
        sb.append("/pa eggtimer stop\n");
        sb.append("/pa eggtimer status\n");
        sb.append("/pa eggtimer duration &lt;minutes&gt;\n\n");
        
        sb.append("<b>Egg Hatcher:</b>\n");
        sb.append("/pa realm toggle - Toggle Egg Hatcher\n");
        sb.append("/pa realm status - Show Egg Hatcher status\n");
        
        return sb.toString();
    }
    
    private String executeStatus() {
        PokeAlertConfig config = ConfigManager.getConfig();
        StringBuilder sb = new StringBuilder();
        sb.append("<b>PokéAlert Status</b>\n\n");
        sb.append("Mod: ").append(config.modEnabled ? "✅ ENABLED" : "❌ DISABLED").append("\n\n");
        
        sb.append("<b>Categories:</b>\n");
        sb.append(formatStatus("Legendaries", config.broadcastAllLegendaries));
        sb.append(formatStatus("Mythics", config.broadcastAllMythics));
        sb.append(formatStatus("Starters", config.broadcastAllStarter));
        sb.append(formatStatus("Babies", config.broadcastAllBabies));
        sb.append(formatStatus("Ultra Beasts", config.broadcastAllUltraBeasts));
        sb.append(formatStatus("Shinies", config.broadcastAllShinies));
        sb.append(formatStatus("Paradox", config.broadcastAllParadox));
        sb.append("\n");
        
        sb.append("<b>Notifications:</b>\n");
        sb.append(formatStatus("In-Game Text", config.inGameTextEnabled));
        sb.append(formatStatus("In-Game Sound", config.inGameSoundEnabled));
        sb.append(formatStatus("Telegram", config.telegramEnabled));
        sb.append("\n");
        
        sb.append("Whitelist: ").append(config.broadcastWhitelist.length).append(" entries\n");
        sb.append("Blacklist: ").append(config.broadcastBlacklist.length).append(" entries\n");
        sb.append("Excluded Worlds: ").append(config.excludedWorlds.length).append(" entries");
        
        return sb.toString();
    }
    
    private String formatStatus(String name, boolean enabled) {
        return "  " + name + ": " + (enabled ? "✅ ON" : "❌ OFF") + "\n";
    }
    
    private String executeEnable(boolean enabled) {
        PokeAlertConfig config = ConfigManager.getConfig();
        config.modEnabled = enabled;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        return "✅ Mod has been " + (enabled ? "ENABLED" : "DISABLED");
    }
    
    private String executeCategory(String category, boolean enable) {
        String catLower = category.toLowerCase();
        PokeAlertConfig config = ConfigManager.getConfig();
        boolean updated = true;
        
        switch (catLower) {
            case "legendaries":
                config.broadcastAllLegendaries = enable;
                break;
            case "mythics":
                config.broadcastAllMythics = enable;
                break;
            case "starters":
                config.broadcastAllStarter = enable;
                break;
            case "babies":
                config.broadcastAllBabies = enable;
                break;
            case "ultrabeasts":
                config.broadcastAllUltraBeasts = enable;
                break;
            case "shinies":
                config.broadcastAllShinies = enable;
                break;
            case "paradox":
                config.broadcastAllParadox = enable;
                break;
            default:
                return "❌ Unknown category: " + category;
        }
        
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        String categoryName = Character.toUpperCase(catLower.charAt(0)) + catLower.substring(1);
        return "✅ " + categoryName + " has been " + (enable ? "ENABLED" : "DISABLED");
    }
    
    private String executeNotification(String type, boolean enable) {
        String typeLower = type.toLowerCase();
        PokeAlertConfig config = ConfigManager.getConfig();
        
        switch (typeLower) {
            case "text":
                config.inGameTextEnabled = enable;
                break;
            case "sound":
                config.inGameSoundEnabled = enable;
                break;
            case "telegram":
                config.telegramEnabled = enable;
                break;
            default:
                return "❌ Unknown notification type: " + type;
        }
        
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        String typeName = Character.toUpperCase(typeLower.charAt(0)) + typeLower.substring(1);
        return "✅ " + typeName + " notifications " + (enable ? "ENABLED" : "DISABLED");
    }
    
    private String executeList(String type) {
        String typeLower = type.toLowerCase();
        PokeAlertConfig config = ConfigManager.getConfig();
        String[] pokemonList;
        String listName;
        
        switch (typeLower) {
            case "legendaries":
                pokemonList = PokemonLists.legendaries;
                listName = "Legendaries";
                break;
            case "mythics":
                pokemonList = PokemonLists.mythics;
                listName = "Mythics";
                break;
            case "starters":
                pokemonList = PokemonLists.starter;
                listName = "Starters";
                break;
            case "babies":
                pokemonList = PokemonLists.babies;
                listName = "Babies";
                break;
            case "ultrabeasts":
                pokemonList = PokemonLists.ultra_beasts;
                listName = "Ultra Beasts";
                break;
            case "shinies":
                // Shinies are not a predefined list - they're detected dynamically
                return "❌ Shinies are detected dynamically, not a predefined list";
            case "paradox":
                pokemonList = PokemonLists.paradox_mons;
                listName = "Paradox";
                break;
            case "whitelist":
                pokemonList = config.broadcastWhitelist;
                listName = "Whitelist";
                break;
            case "blacklist":
                pokemonList = config.broadcastBlacklist;
                listName = "Blacklist";
                break;
            default:
                return "❌ Unknown list type: " + type;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(listName).append(" (").append(pokemonList.length).append(")</b>\n");
        
        if (pokemonList.length == 0) {
            sb.append("Empty list");
        } else {
            for (String pokemon : pokemonList) {
                sb.append("  • ").append(formatPokemonName(pokemon)).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    private String formatPokemonName(String name) {
        String[] parts = name.split("[ -]");
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                formatted.append(name.contains("-") ? "-" : " ");
            }
            if (!parts[i].isEmpty()) {
                formatted.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) {
                    formatted.append(parts[i].substring(1));
                }
            }
        }
        return formatted.toString();
    }
    
    private String executeWhitelistAdd(String pokemon) {
        PokeAlertConfig config = ConfigManager.getConfig();
        List<String> whitelist = new ArrayList<>(Arrays.asList(config.broadcastWhitelist));
        String pokemonLower = pokemon.toLowerCase();
        
        if (whitelist.contains(pokemonLower)) {
            return "⚠️ " + pokemon + " is already in whitelist";
        }
        
        whitelist.add(pokemonLower);
        config.broadcastWhitelist = whitelist.toArray(new String[0]);
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        return "✅ Added " + pokemon + " to whitelist";
    }
    
    private String executeWhitelistRemove(String pokemon) {
        PokeAlertConfig config = ConfigManager.getConfig();
        List<String> whitelist = new ArrayList<>(Arrays.asList(config.broadcastWhitelist));
        String pokemonLower = pokemon.toLowerCase();
        
        if (!whitelist.remove(pokemonLower)) {
            return "⚠️ " + pokemon + " is not in whitelist";
        }
        
        config.broadcastWhitelist = whitelist.toArray(new String[0]);
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        return "✅ Removed " + pokemon + " from whitelist";
    }
    
    private String executeWhitelistList() {
        return executeList("whitelist");
    }
    
    private String executeBlacklistAdd(String pokemon) {
        PokeAlertConfig config = ConfigManager.getConfig();
        List<String> blacklist = new ArrayList<>(Arrays.asList(config.broadcastBlacklist));
        String pokemonLower = pokemon.toLowerCase();
        
        if (blacklist.contains(pokemonLower)) {
            return "⚠️ " + pokemon + " is already in blacklist";
        }
        
        blacklist.add(pokemonLower);
        config.broadcastBlacklist = blacklist.toArray(new String[0]);
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        return "✅ Added " + pokemon + " to blacklist";
    }
    
    private String executeBlacklistRemove(String pokemon) {
        PokeAlertConfig config = ConfigManager.getConfig();
        List<String> blacklist = new ArrayList<>(Arrays.asList(config.broadcastBlacklist));
        String pokemonLower = pokemon.toLowerCase();
        
        if (!blacklist.remove(pokemonLower)) {
            return "⚠️ " + pokemon + " is not in blacklist";
        }
        
        config.broadcastBlacklist = blacklist.toArray(new String[0]);
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        return "✅ Removed " + pokemon + " from blacklist";
    }
    
    private String executeBlacklistList() {
        return executeList("blacklist");
    }
    
    private String executeEggTimerStart(int minutes) {
        EggTimerManager timerManager = EggTimerManager.getInstance();
        
        if (timerManager.isTimerRunning()) {
            int remaining = timerManager.getRemainingMinutes();
            return "⏰ Egg timer already running: " + remaining + " minutes remaining";
        }
        
        if (minutes > 0) {
            timerManager.startTimer(minutes);
            return "✅ Egg timer started for " + minutes + " minutes";
        } else {
            timerManager.startTimer();
            return "✅ Egg timer started";
        }
    }
    
    private String executeEggTimerStop() {
        EggTimerManager timerManager = EggTimerManager.getInstance();
        
        if (timerManager.stopTimer()) {
            return "✅ Egg timer stopped";
        } else {
            return "⚠️ No egg timer is running";
        }
    }
    
    private String executeEggTimerStatus() {
        EggTimerManager timerManager = EggTimerManager.getInstance();
        
        if (timerManager.isTimerRunning()) {
            int remaining = timerManager.getRemainingMinutes();
            return "⏰ Egg timer: " + remaining + " minutes remaining";
        } else {
            return "⏰ No egg timer is running";
        }
    }
    
    private String executeEggTimerDuration(int minutes) {
        PokeAlertConfig config = ConfigManager.getConfig();
        config.eggTimerDuration = minutes;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        return "✅ Default egg timer duration set to " + minutes + " minutes";
    }
    
    private String executeRealmToggle() {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        if (!config.modEnabled) {
            return "⚠️ Egg Hatcher disabled - Enable PokéAlert first";
        }
        
        EggHatcher manager = EggHatcher.getInstance();
        manager.toggleAutomation();
        
        String status = manager.getStatus();
        return "✅ Egg Hatcher: " + status;
    }
    
    private String executeRealmStatus() {
        EggHatcher manager = EggHatcher.getInstance();
        String status = manager.getStatus();
        
        StringBuilder sb = new StringBuilder();
        sb.append("<b>Egg Hatcher Status:</b> ").append(status).append("\n");
        
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (manager.isAtSpawn() && client != null && client.player != null) {
            sb.append("⚠️ Currently at spawn world");
        }
        
        return sb.toString();
    }
    
    /**
     * Send response back to Telegram
     */
    private void sendTelegramResponse(long chatId, String message, int replyToMessageId) {
        try {
            config = ConfigManager.getConfig(); // Refresh config
            
            PokeAlertClient.LOGGER.info("TelegramCommandReceiver: Sending response to chat {}: {}", chatId, message);
            
            JsonObject jsonPayload = new JsonObject();
            // For group chats (negative IDs), Telegram API accepts both string and number
            // Using string format to ensure compatibility
            jsonPayload.addProperty("chat_id", String.valueOf(chatId));
            jsonPayload.addProperty("text", message);
            if (replyToMessageId > 0) {
                jsonPayload.addProperty("reply_to_message_id", replyToMessageId);
            }
            jsonPayload.addProperty("parse_mode", "HTML");
            
            String url = String.format(
                "%s/bot%s/sendMessage",
                config.telegramApiUrl,
                config.telegramBotToken
            );
            
            PokeAlertClient.LOGGER.debug("TelegramCommandReceiver: Sending to URL: {}", url.replace(config.telegramBotToken, "***"));
            PokeAlertClient.LOGGER.debug("TelegramCommandReceiver: Payload: {}", jsonPayload.toString());
            
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
                    PokeAlertClient.LOGGER.error("TelegramCommandReceiver: Failed to send response to chat {}: {}", chatId, e.getMessage(), e);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "No body";
                        PokeAlertClient.LOGGER.warn("TelegramCommandReceiver: Failed to send response to chat {}: {} - {}", 
                            chatId, response.code(), errorBody);
                    } else {
                        PokeAlertClient.LOGGER.info("TelegramCommandReceiver: Successfully sent response to chat {}", chatId);
                    }
                    response.close();
                }
            });
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("TelegramCommandReceiver: Error sending response to chat {}: {}", chatId, e.getMessage(), e);
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

