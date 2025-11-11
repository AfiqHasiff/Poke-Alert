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
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> currentTimer;
    private long timerStartTime;
    private int currentDuration;
    
    private EggTimerManager() {}
    
    public static EggTimerManager getInstance() {
        if (instance == null) {
            instance = new EggTimerManager();
        }
        return instance;
    }
    
    public boolean startTimer() {
        // Cancel any existing timer
        if (isTimerRunning()) {
            return false; // Timer already running
        }
        
        PokeAlertConfig config = ConfigManager.getConfig();
        currentDuration = config.eggTimerDuration;
        timerStartTime = System.currentTimeMillis();
        
        // Send start notification
        sendStartNotification();
        
        // Schedule the timer completion
        currentTimer = scheduler.schedule(() -> {
            sendCompletionNotification();
            currentTimer = null;
        }, currentDuration, TimeUnit.MINUTES);
        
        return true;
    }
    
    public boolean startTimer(int durationMinutes) {
        // Cancel any existing timer
        if (isTimerRunning()) {
            return false; // Timer already running
        }
        
        currentDuration = durationMinutes;
        timerStartTime = System.currentTimeMillis();
        
        // Send start notification
        sendStartNotification();
        
        // Schedule the timer completion
        currentTimer = scheduler.schedule(() -> {
            sendCompletionNotification();
            currentTimer = null;
        }, durationMinutes, TimeUnit.MINUTES);
        
        return true;
    }
    
    public boolean stopTimer() {
        if (currentTimer != null && !currentTimer.isDone()) {
            currentTimer.cancel(false);
            currentTimer = null;
            sendCancelNotification();
            return true;
        }
        return false;
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
        
        // In-game text notification
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
        
        // Telegram notification
        if (config.eggTimerTelegramNotification && config.telegramEnabled) {
            CompletableFuture.runAsync(() -> {
                TelegramNotification telegram = new TelegramNotification();
                telegram.sendEggTimerNotification("🥚 Egg Timer Started! Duration: " + currentDuration + " minutes");
            });
        }
    }
    
    private void sendCompletionNotification() {
        PokeAlertConfig config = ConfigManager.getConfig();
        MinecraftClient client = MinecraftClient.getInstance();
        
        // In-game text notification  
        if (config.eggTimerTextNotification && config.inGameTextEnabled && client.player != null) {
            client.execute(() -> {
                // Play notification sound if enabled
                if (config.inGameSoundEnabled) {
                    client.player.playSound(
                        PokeAlertClient.NOTIFICATION_SOUND_EVENT,
                        config.inGameSoundVolume,
                        1.0f
                    );
                }
                
                client.player.sendMessage(
                    Text.literal("[").formatted(Formatting.GRAY)
                        .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                        .append(Text.literal("] ").formatted(Formatting.GRAY))
                        .append(Text.literal("⏰ ").formatted(Formatting.YELLOW))
                        .append(Text.literal("Egg timer completed! ").formatted(Formatting.GREEN, Formatting.BOLD))
                        .append(Text.literal("(" + currentDuration + " minutes)").formatted(Formatting.AQUA))
                );
            });
        }
        
        // Telegram notification
        if (config.eggTimerTelegramNotification && config.telegramEnabled) {
            CompletableFuture.runAsync(() -> {
                TelegramNotification telegram = new TelegramNotification();
                telegram.sendEggTimerNotification("🎉 Egg Timer Completed! Your " + currentDuration + " minute timer has finished!");
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
