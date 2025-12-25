package com.afiqhasiff.pokealert.client.util;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.config.PokeAlertConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the queue of movement destinations for Anti-AFK.
 * Automatically replenishes when running low to ensure perpetual movement.
 * 
 * v3.0.0: Core component of the new internal Anti-AFK system.
 * 
 * Queue Behavior:
 * - Initializes with configurable number of random locations
 * - Replenishes automatically when at second-to-last location
 * - Tracks visited locations for Step 5 completion check
 * - Handles timeouts and tracks consecutive timeout count
 * - Auto-trims old locations to prevent memory growth
 */
public class LocationQueue {
    private final List<int[]> locations = new ArrayList<>();
    private int currentIndex = 0;
    private int visitedCount = 0;
    private int consecutiveTimeouts = 0;
    private boolean step5Completed = false;
    
    // Region for generating new locations
    private AntiAfkRegion region;
    
    // Configuration (set during initialize)
    private int initialQueueSize = 5;
    private int replenishCount = 3;
    private int locationsForStep5 = 3;
    private int maxConsecutiveTimeouts = 3;
    
    /**
     * Default constructor.
     * Call initialize() to set up the queue with a region and config.
     */
    public LocationQueue() {
        // Configuration set during initialize()
    }
    
    /**
     * Initialize queue with random locations from the specified region.
     * 
     * @param region The Anti-AFK region to generate locations within
     * @param config Configuration for queue settings
     */
    public void initialize(AntiAfkRegion region, PokeAlertConfig config) {
        this.region = region;
        this.initialQueueSize = config.initialQueueSize;
        this.replenishCount = config.replenishCount;
        this.locationsForStep5 = config.locationsForStep6; // Config uses locationsForStep6 for v2 compat
        this.maxConsecutiveTimeouts = config.maxConsecutiveTimeouts;
        
        // Clear and repopulate
        locations.clear();
        currentIndex = 0;
        visitedCount = 0;
        consecutiveTimeouts = 0;
        step5Completed = false;
        
        // Generate initial locations
        for (int i = 0; i < initialQueueSize; i++) {
            locations.add(region.getRandomPosition());
        }
        
        PokeAlertClient.LOGGER.info("LocationQueue: Initialized with {} locations (need {} for Step 5)",
            initialQueueSize, locationsForStep5);
    }
    
    /**
     * Get the current destination coordinates.
     * 
     * @return int[] with [x, z], or null if queue is empty
     */
    public int[] getCurrentDestination() {
        if (currentIndex < locations.size()) {
            return locations.get(currentIndex);
        }
        return null;
    }
    
    /**
     * Mark current location as visited and advance to next.
     * Handles queue replenishment and trimming automatically.
     */
    public void markVisitedAndAdvance() {
        if (currentIndex < locations.size()) {
            int[] visited = locations.get(currentIndex);
            visitedCount++;
            consecutiveTimeouts = 0; // Reset on successful visit
            
            PokeAlertClient.LOGGER.info("LocationQueue: Visited ({}, {}) - Total: {}/{}",
                visited[0], visited[1], visitedCount, locationsForStep5);
            
            currentIndex++;
            checkAndReplenish();
            trimOldLocations();
        }
    }
    
    /**
     * Mark current location as timed out (unreachable) and skip to next.
     * 
     * @return true if should continue (haven't hit max timeouts), false if should stop
     */
    public boolean markTimeoutAndAdvance() {
        if (currentIndex < locations.size()) {
            int[] skipped = locations.get(currentIndex);
            consecutiveTimeouts++;
            
            PokeAlertClient.LOGGER.warn("LocationQueue: Timeout at ({}, {}) - Consecutive: {}/{}",
                skipped[0], skipped[1], consecutiveTimeouts, maxConsecutiveTimeouts);
            
            currentIndex++;
            checkAndReplenish();
            trimOldLocations();
        }
        
        return consecutiveTimeouts < maxConsecutiveTimeouts;
    }
    
    /**
     * Get total number of successfully visited locations.
     */
    public int getVisitedCount() {
        return visitedCount;
    }
    
    /**
     * Get number of consecutive timeouts.
     */
    public int getConsecutiveTimeouts() {
        return consecutiveTimeouts;
    }
    
    /**
     * Check if there's a previous location to backtrack to.
     * @return true if currentIndex > 0
     */
    public boolean hasPrevious() {
        return currentIndex > 0;
    }
    
    /**
     * Get the previous location (before backtracking).
     * @return int[] with [x, z] of previous location, or null if no previous location
     */
    public int[] getPreviousDestination() {
        if (currentIndex > 0) {
            return locations.get(currentIndex - 1);
        }
        return null;
    }
    
    /**
     * Go back to the previous location (backtracking behavior).
     * Decrements currentIndex to revisit the last visited location.
     */
    public void goToPrevious() {
        if (currentIndex > 0) {
            currentIndex--;
            PokeAlertClient.LOGGER.info("LocationQueue: Backtracking to previous location at index {}", currentIndex);
        }
    }
    
    /**
     * Check if Step 5 should trigger (enough visits, not already completed).
     */
    public boolean shouldTriggerStep5() {
        return visitedCount >= locationsForStep5 && !step5Completed;
    }
    
    /**
     * Mark Step 5 as completed.
     * This is a checkpoint - Anti-AFK continues running after this.
     */
    public void markStep5Completed() {
        step5Completed = true;
        PokeAlertClient.LOGGER.info("LocationQueue: Step 5 completed (visited {} locations)", visitedCount);
    }
    
    /**
     * Check if Step 5 has been completed.
     */
    public boolean isStep5Completed() {
        return step5Completed;
    }
    
    /**
     * Get current queue size.
     */
    public int getQueueSize() {
        return locations.size();
    }
    
    /**
     * Get remaining locations in queue.
     */
    public int getRemainingLocations() {
        return locations.size() - currentIndex;
    }
    
    /**
     * Reset queue for a new cycle.
     * Keeps the same region but regenerates locations.
     */
    public void reset() {
        if (region != null) {
            PokeAlertConfig config = new PokeAlertConfig();
            config.initialQueueSize = this.initialQueueSize;
            config.replenishCount = this.replenishCount;
            config.locationsForStep6 = this.locationsForStep5;
            config.maxConsecutiveTimeouts = this.maxConsecutiveTimeouts;
            initialize(region, config);
        }
    }
    
    /**
     * Check if queue needs replenishment and add more locations.
     * Triggers when at second-to-last location.
     */
    private void checkAndReplenish() {
        int remaining = locations.size() - currentIndex;
        
        // Replenish when at second-to-last (or less)
        if (remaining <= 2 && region != null) {
            for (int i = 0; i < replenishCount; i++) {
                locations.add(region.getRandomPosition());
            }
            
            PokeAlertClient.LOGGER.info("LocationQueue: Replenished with {} new locations (total: {})",
                replenishCount, locations.size());
        }
    }
    
    /**
     * Trim old locations from the front to prevent unbounded growth.
     * Keeps at least initialQueueSize locations.
     */
    private void trimOldLocations() {
        // Only trim if we have more than double the initial size
        if (currentIndex > initialQueueSize) {
            int trimCount = currentIndex - initialQueueSize;
            
            // Remove from front
            for (int i = 0; i < trimCount; i++) {
                locations.remove(0);
            }
            
            // Adjust current index
            currentIndex -= trimCount;
            
            PokeAlertClient.LOGGER.debug("LocationQueue: Trimmed {} old locations (index now: {})",
                trimCount, currentIndex);
        }
    }
    
    /**
     * Get status string for debugging/display.
     */
    public String getStatus() {
        if (step5Completed) {
            return String.format("Location %d/%d (Step 5 done)", currentIndex + 1, locations.size());
        }
        return String.format("Location %d/%d (visited: %d/%d)",
            currentIndex + 1, locations.size(), visitedCount, locationsForStep5);
    }
}
