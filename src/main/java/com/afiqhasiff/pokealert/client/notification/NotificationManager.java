package com.afiqhasiff.pokealert.client.notification;

import com.afiqhasiff.pokealert.client.PokeAlertClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manages multiple notification services and coordinates sending notifications.
 * Handles rate limiting and prevents duplicate notifications.
 */
public class NotificationManager {
    private final List<NotificationService> services;
    private final Map<String, Long> notificationCooldowns;
    private final long cooldownMillis;

    public NotificationManager(long cooldownMillis) {
        this.services = new ArrayList<>();
        this.notificationCooldowns = new HashMap<>();
        this.cooldownMillis = cooldownMillis;
    }

    /**
     * Register a notification service
     */
    public void registerService(NotificationService service) {
        services.add(service);
        service.initialize();
        PokeAlertClient.LOGGER.info("Registered notification service: {}", service.getServiceName());
    }

    /**
     * Send notification to all enabled services
     */
    public void notifyAll(PokemonSpawnData data) {
        // Check cooldown
        String cooldownKey = getCooldownKey(data);
        long now = System.currentTimeMillis();
        
        if (notificationCooldowns.containsKey(cooldownKey)) {
            long lastNotification = notificationCooldowns.get(cooldownKey);
            if (now - lastNotification < cooldownMillis) {
                // Still in cooldown period
                return;
            }
        }

        // Update cooldown
        notificationCooldowns.put(cooldownKey, now);
        
        // Clean up old cooldowns (optional optimization)
        cleanupOldCooldowns(now);

        // Send to all enabled services
        for (NotificationService service : services) {
            if (service.isEnabled()) {
                try {
                    // Run async to avoid blocking game thread
                    CompletableFuture.runAsync(() -> {
                        try {
                            service.sendNotification(data);
                        } catch (Exception e) {
                            PokeAlertClient.LOGGER.error(
                                "Error sending notification via {}: {}",
                                service.getServiceName(),
                                e.getMessage()
                            );
                        }
                    });
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.error(
                        "Error dispatching notification to {}: {}",
                        service.getServiceName(),
                        e.getMessage()
                    );
                }
            }
        }
    }

    /**
     * Generate a cooldown key for a Pokemon spawn
     */
    private String getCooldownKey(PokemonSpawnData data) {
        // Use location-based key to prevent duplicate notifications for same spawn
        return String.format("%s_%d_%d_%d", data.getPokemonName(), data.getX(), data.getY(), data.getZ());
    }

    /**
     * Clean up cooldowns older than the cooldown period
     */
    private void cleanupOldCooldowns(long currentTime) {
        notificationCooldowns.entrySet().removeIf(
            entry -> currentTime - entry.getValue() > cooldownMillis * 2
        );
    }

    /**
     * Shutdown all services
     */
    public void shutdown() {
        for (NotificationService service : services) {
            try {
                service.shutdown();
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error(
                    "Error shutting down {}: {}",
                    service.getServiceName(),
                    e.getMessage()
                );
            }
        }
        services.clear();
        notificationCooldowns.clear();
    }

    /**
     * Get list of registered services
     */
    public List<NotificationService> getServices() {
        return new ArrayList<>(services);
    }
}

