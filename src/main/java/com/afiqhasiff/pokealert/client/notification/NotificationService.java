package com.afiqhasiff.pokealert.client.notification;

/**
 * Abstract base class for notification services.
 * Implementations can provide different ways to notify users of Pokemon spawns.
 */
public abstract class NotificationService {
    
    /**
     * Send a notification about a Pokemon spawn
     * @param data The Pokemon spawn data to notify about
     */
    public abstract void sendNotification(PokemonSpawnData data);
    
    /**
     * Check if this notification service is enabled and ready to send notifications
     * @return true if the service should be used
     */
    public abstract boolean isEnabled();
    
    /**
     * Initialize the notification service
     * Called when the mod initializes
     */
    public abstract void initialize();
    
    /**
     * Get the name of this notification service
     * @return A human-readable name for this service
     */
    public abstract String getServiceName();
    
    /**
     * Clean up resources when shutting down
     * Optional to implement
     */
    public void shutdown() {
        // Default: no-op
    }
}

