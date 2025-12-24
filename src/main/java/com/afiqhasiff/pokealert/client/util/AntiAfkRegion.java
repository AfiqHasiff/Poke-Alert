package com.afiqhasiff.pokealert.client.util;

import java.util.Random;

/**
 * Represents a rectangular region for Anti-AFK movement.
 * Defined by two corner coordinates (X1, Z1) and (X2, Z2).
 * 
 * v3.0.0: Core component of the new internal Anti-AFK system.
 * 
 * Design Decisions:
 * - No normalize() method - coordinates can be in any order
 * - Uses getMinX/getMaxX/getMinZ/getMaxZ for consistent access
 * - hasValidArea() checks for non-zero area (not just valid coordinates)
 * - getRandomPosition() generates truly random positions within region
 */
public class AntiAfkRegion {
    // Corner 1
    public final int x1;
    public final int z1;
    
    // Corner 2
    public final int x2;
    public final int z2;
    
    private final Random random = new Random();
    
    /**
     * Create a new region from two corner coordinates.
     * Coordinates can be in any order (x1 > x2 is valid).
     */
    public AntiAfkRegion(int x1, int z1, int x2, int z2) {
        this.x1 = x1;
        this.z1 = z1;
        this.x2 = x2;
        this.z2 = z2;
    }
    
    /**
     * Get the minimum X coordinate of the region.
     */
    public int getMinX() {
        return Math.min(x1, x2);
    }
    
    /**
     * Get the maximum X coordinate of the region.
     */
    public int getMaxX() {
        return Math.max(x1, x2);
    }
    
    /**
     * Get the minimum Z coordinate of the region.
     */
    public int getMinZ() {
        return Math.min(z1, z2);
    }
    
    /**
     * Get the maximum Z coordinate of the region.
     */
    public int getMaxZ() {
        return Math.max(z1, z2);
    }
    
    /**
     * Get the width of the region (X axis).
     */
    public int getWidth() {
        return getMaxX() - getMinX();
    }
    
    /**
     * Get the depth of the region (Z axis).
     */
    public int getDepth() {
        return getMaxZ() - getMinZ();
    }
    
    /**
     * Generate a random X, Z position within the region.
     * 
     * @return int[] with [x, z] coordinates
     */
    public int[] getRandomPosition() {
        int minX = getMinX();
        int maxX = getMaxX();
        int minZ = getMinZ();
        int maxZ = getMaxZ();
        
        // Generate random position within bounds
        int randomX = minX + random.nextInt(Math.max(1, maxX - minX + 1));
        int randomZ = minZ + random.nextInt(Math.max(1, maxZ - minZ + 1));
        
        return new int[] { randomX, randomZ };
    }
    
    /**
     * Check if a given position is within the region bounds.
     * 
     * @param x X coordinate to check
     * @param z Z coordinate to check
     * @return true if the position is within the region
     */
    public boolean contains(int x, int z) {
        return x >= getMinX() && x <= getMaxX() &&
               z >= getMinZ() && z <= getMaxZ();
    }
    
    /**
     * Check if the region has a valid (non-zero) area.
     * A region needs both width and depth > 0 to be valid.
     * 
     * @return true if the region has valid area
     */
    public boolean hasValidArea() {
        return getWidth() > 0 && getDepth() > 0;
    }
    
    /**
     * Check if the region is too small for effective Anti-AFK.
     * Recommended minimum size is 20x20 blocks.
     * 
     * @return true if region is smaller than 20x20
     */
    public boolean isTooSmall() {
        return getWidth() < 20 || getDepth() < 20;
    }
    
    /**
     * Get the area of the region in blocks^2.
     */
    public int getArea() {
        return getWidth() * getDepth();
    }
    
    @Override
    public String toString() {
        return String.format("AntiAfkRegion[(%d,%d) to (%d,%d) - %dx%d]",
            x1, z1, x2, z2, getWidth(), getDepth());
    }
}
