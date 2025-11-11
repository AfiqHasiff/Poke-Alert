package com.afiqhasiff.pokealert.client.notification;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.config.ConfigManager;
import com.afiqhasiff.pokealert.client.config.PokeAlertConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class EggTimerManager {
    private static EggTimerManager instance;
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private ScheduledFuture<?> currentTimer;
    private ScheduledFuture<?> reminderTimer;
    private long timerStartTime;
    private int currentDuration;
    private long lastCancelPromptTime = 0;
    private static final long CANCEL_CONFIRM_WINDOW = 3000; // 3 seconds in milliseconds
    
    private EggTimerManager() {}
    
    public static EggTimerManager getInstance() {
        if (instance == null) {
            instance = new EggTimerManager();
        }
        return instance;
    }
    
    public boolean startTimer() {
        PokeAlertConfig config = ConfigManager.getConfig();
        return startTimer(config.eggTimerDuration);
    }
    
    public boolean startTimer(int durationMinutes) {
        // Cancel any existing timer
        if (isTimerRunning()) {
            return false; // Timer already running
        }
        
        currentDuration = durationMinutes;
        timerStartTime = System.currentTimeMillis();
        
        // Send start notification (only in-game text, no sound or Telegram)
        sendStartNotification();
        
        // Schedule the timer completion
        currentTimer = scheduler.schedule(() -> {
            sendCompletionNotification();
            stopReminders();
            currentTimer = null;
        }, durationMinutes, TimeUnit.MINUTES);
        
        // Schedule reminders every 5 minutes
        scheduleReminders();
        
        return true;
    }
    
    public boolean handleTimerToggle() {
        if (!isTimerRunning()) {
            // No timer running, start a new one
            return startTimer();
        } else {
            // Timer is running, check for cancel confirmation
            long currentTime = System.currentTimeMillis();
            
            if (currentTime - lastCancelPromptTime <= CANCEL_CONFIRM_WINDOW) {
                // Within confirmation window, cancel the timer
                stopTimer();
                lastCancelPromptTime = 0;
                return false;
            } else {
                // Show confirmation prompt
                showCancelConfirmation();
                lastCancelPromptTime = currentTime;
                return true;
            }
        }
    }
    
    public boolean stopTimer() {
        if (currentTimer != null && !currentTimer.isDone()) {
            currentTimer.cancel(false);
            currentTimer = null;
            stopReminders();
            sendCancelNotification();
            return true;
        }
        return false;
    }
    
    private void stopReminders() {
        if (reminderTimer != null && !reminderTimer.isDone()) {
            reminderTimer.cancel(false);
            reminderTimer = null;
        }
    }
    
    private void scheduleReminders() {
        // Schedule reminders every 5 minutes
        reminderTimer = scheduler.scheduleAtFixedRate(() -> {
            int remaining = getRemainingMinutes();
            if (remaining > 0 && remaining % 5 == 0) { // Only send when exactly divisible by 5
                sendReminderNotification(remaining);
            }
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    private void sendReminderNotification(int remainingMinutes) {
        MinecraftClient client = MinecraftClient.getInstance();
        PokeAlertConfig config = ConfigManager.getConfig();
        
        if (config.eggTimerTextNotification && config.inGameTextEnabled && client.player != null) {
            // Calculate expected end time
            long endTimeMillis = timerStartTime + (currentDuration * 60 * 1000);
            String endTime = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date(endTimeMillis));
            
            client.execute(() -> {
                client.player.sendMessage(
                    Text.literal("[").formatted(Formatting.GRAY)
                        .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                        .append(Text.literal("] ").formatted(Formatting.GRAY))
                        .append(Text.literal("⏰ ").formatted(Formatting.YELLOW))
                        .append(Text.literal("Egg timer reminder: ").formatted(Formatting.WHITE))
                        .append(Text.literal(remainingMinutes + " minutes").formatted(Formatting.AQUA))
                        .append(Text.literal(" remaining (ends at ").formatted(Formatting.GRAY))
                        .append(Text.literal(endTime).formatted(Formatting.GOLD))
                        .append(Text.literal(")").formatted(Formatting.GRAY))
                );
            });
        }
    }
    
    private void showCancelConfirmation() {
        MinecraftClient client = MinecraftClient.getInstance();
        PokeAlertConfig config = ConfigManager.getConfig();
        
        if (config.inGameTextEnabled && client.player != null) {
            int remaining = getRemainingMinutes();
            client.execute(() -> {
                client.player.sendMessage(
                    Text.literal("[").formatted(Formatting.GRAY)
                        .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                        .append(Text.literal("] ").formatted(Formatting.GRAY))
                        .append(Text.literal("⏰ ").formatted(Formatting.YELLOW))
                        .append(Text.literal("Egg timer: ").formatted(Formatting.WHITE))
                        .append(Text.literal(remaining + " minutes").formatted(Formatting.AQUA))
                        .append(Text.literal(" remaining. Press ").formatted(Formatting.WHITE))
                        .append(Text.literal("'").formatted(Formatting.GOLD))
                        .append(Text.literal(" again to cancel").formatted(Formatting.WHITE))
                );
            });
        }
    }
    
    public boolean isTimerRunning() {
        return currentTimer != null && !currentTimer.isDone();
    }
    
    public int getRemainingMinutes() {
        if (!isTimerRunning()) {
            return 0;
        }
        
        long elapsedMillis = System.currentTimeMillis() - timerStartTime;
        long remainingMillis = (currentDuration * 60 * 1000) - elapsedMillis;
        return (int) Math.ceil(remainingMillis / 60000.0);
    }
    
    private void sendStartNotification() {
        PokeAlertConfig config = ConfigManager.getConfig();
        MinecraftClient client = MinecraftClient.getInstance();
        
        // Only send in-game text notification on start (no sound or Telegram)
        if (config.eggTimerTextNotification && config.inGameTextEnabled && client.player != null) {
            client.execute(() -> {
                client.player.sendMessage(
                    Text.literal("[").formatted(Formatting.GRAY)
                        .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                        .append(Text.literal("] ").formatted(Formatting.GRAY))
                        .append(Text.literal("⏰ ").formatted(Formatting.YELLOW))
                        .append(Text.literal("Egg timer started: ").formatted(Formatting.WHITE))
                        .append(Text.literal(currentDuration + " minutes").formatted(Formatting.AQUA))
                );
            });
        }
    }
    
    private void sendCompletionNotification() {
        PokeAlertConfig config = ConfigManager.getConfig();
        MinecraftClient client = MinecraftClient.getInstance();
        
        // Calculate start time
        String startTime = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date(timerStartTime));
        
        // In-game notifications (text and sound)
        if (client.player != null) {
            client.execute(() -> {
                // Play notification sound if enabled
                if (config.inGameSoundEnabled) {
                    client.player.playSound(
                        PokeAlertClient.NOTIFICATION_SOUND_EVENT,
                        config.inGameSoundVolume,
                        1.0f
                    );
                }
                
                // Send text notification
                if (config.eggTimerTextNotification && config.inGameTextEnabled) {
                    client.player.sendMessage(
                        Text.literal("[").formatted(Formatting.GRAY)
                            .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                            .append(Text.literal("] ").formatted(Formatting.GRAY))
                            .append(Text.literal("⏰ ").formatted(Formatting.YELLOW))
                            .append(Text.literal("Egg timer completed! ").formatted(Formatting.GREEN, Formatting.BOLD))
                            .append(Text.literal("(").formatted(Formatting.GRAY))
                            .append(Text.literal(currentDuration + " min").formatted(Formatting.AQUA))
                            .append(Text.literal(" timer started at ").formatted(Formatting.GRAY))
                            .append(Text.literal(startTime).formatted(Formatting.GOLD))
                            .append(Text.literal(")").formatted(Formatting.GRAY))
                    );
                }
            });
        }
        
        // Telegram notification using StringBuilder pattern
        if (config.eggTimerTelegramNotification && config.telegramEnabled) {
            CompletableFuture.runAsync(() -> {
                TelegramNotification telegram = new TelegramNotification();
                
                // Build message using StringBuilder with existing pattern
                StringBuilder message = new StringBuilder();
                message.append("<b>🎉 Egg Timer Complete!</b>\n");
                message.append("• Duration: <i>").append(currentDuration).append(" minutes</i>\n");
                message.append("• Started: <code>").append(startTime).append("</code>");
                
                telegram.sendEggTimerNotification(message.toString());
            });
        }
    }
    
    private void sendCancelNotification() {
        MinecraftClient client = MinecraftClient.getInstance();
        PokeAlertConfig config = ConfigManager.getConfig();
        
        if (config.eggTimerTextNotification && config.inGameTextEnabled && client.player != null) {
            client.execute(() -> {
                client.player.sendMessage(
                    Text.literal("[").formatted(Formatting.GRAY)
                        .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                        .append(Text.literal("] ").formatted(Formatting.GRAY))
                        .append(Text.literal("⏰ ").formatted(Formatting.YELLOW))
                        .append(Text.literal("Egg timer cancelled").formatted(Formatting.RED))
                );
            });
        }
    }
    
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
