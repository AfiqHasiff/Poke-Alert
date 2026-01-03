package com.afiqhasiff.pokealert.client.config;

/**
 * Stores coordinate mappings for PC and Party slots, plus box navigation arrows.
 * Used for manual calibration of slot positions for accurate drag-and-drop transfers.
 */
public class SlotCoordinateMapping {
    // Visual indicator toggle
    public boolean visualIndicatorsEnabled = false;
    
    // PC Box Slots (30 slots: slot1-slot30, 0-indexed internally)
    public SlotCoordinate[] boxSlots = new SlotCoordinate[30];
    
    // Party Slots (6 slots: slot1-slot6, 0-indexed internally)
    public SlotCoordinate[] partySlots = new SlotCoordinate[6];
    
    // Box Navigation Arrows
    public SlotCoordinate boxArrowLeft = new SlotCoordinate();
    public SlotCoordinate boxArrowRight = new SlotCoordinate();
    
    public SlotCoordinateMapping() {
        // Initialize all slots with default coordinates (0, 0 = unmapped)
        for (int i = 0; i < 30; i++) {
            boxSlots[i] = new SlotCoordinate();
        }
        for (int i = 0; i < 6; i++) {
            partySlots[i] = new SlotCoordinate();
        }
    }
    
    /**
     * Get PC slot coordinate (1-indexed: slot1 = 0, slot30 = 29)
     */
    public SlotCoordinate getBoxSlot(int slotIndex) {
        if (slotIndex < 1 || slotIndex > 30) {
            return new SlotCoordinate(); // Invalid slot
        }
        return boxSlots[slotIndex - 1];
    }
    
    /**
     * Set PC slot coordinate (1-indexed: slot1 = 0, slot30 = 29)
     */
    public void setBoxSlot(int slotIndex, int x, int y) {
        if (slotIndex >= 1 && slotIndex <= 30) {
            boxSlots[slotIndex - 1] = new SlotCoordinate(x, y);
        }
    }
    
    /**
     * Get Party slot coordinate (1-indexed: slot1 = 0, slot6 = 5)
     */
    public SlotCoordinate getPartySlot(int slotIndex) {
        if (slotIndex < 1 || slotIndex > 6) {
            return new SlotCoordinate(); // Invalid slot
        }
        return partySlots[slotIndex - 1];
    }
    
    /**
     * Set Party slot coordinate (1-indexed: slot1 = 0, slot6 = 5)
     */
    public void setPartySlot(int slotIndex, int x, int y) {
        if (slotIndex >= 1 && slotIndex <= 6) {
            partySlots[slotIndex - 1] = new SlotCoordinate(x, y);
        }
    }
    
    /**
     * Check if a PC slot is mapped (has non-zero coordinates)
     */
    public boolean isBoxSlotMapped(int slotIndex) {
        SlotCoordinate coord = getBoxSlot(slotIndex);
        return coord.x != 0 || coord.y != 0;
    }
    
    /**
     * Check if a Party slot is mapped (has non-zero coordinates)
     */
    public boolean isPartySlotMapped(int slotIndex) {
        SlotCoordinate coord = getPartySlot(slotIndex);
        return coord.x != 0 || coord.y != 0;
    }
    
    /**
     * Check if box arrow is mapped
     */
    public boolean isBoxArrowLeftMapped() {
        return boxArrowLeft.x != 0 || boxArrowLeft.y != 0;
    }
    
    public boolean isBoxArrowRightMapped() {
        return boxArrowRight.x != 0 || boxArrowRight.y != 0;
    }
    
    /**
     * Get count of mapped PC slots
     */
    public int getMappedBoxSlotCount() {
        int count = 0;
        for (int i = 1; i <= 30; i++) {
            if (isBoxSlotMapped(i)) count++;
        }
        return count;
    }
    
    /**
     * Get count of mapped Party slots
     */
    public int getMappedPartySlotCount() {
        int count = 0;
        for (int i = 1; i <= 6; i++) {
            if (isPartySlotMapped(i)) count++;
        }
        return count;
    }
    
    /**
     * Check if all slots are mapped (for validation)
     */
    public boolean areAllSlotsMapped() {
        return getMappedBoxSlotCount() == 30 && getMappedPartySlotCount() == 6;
    }
    
    /**
     * Inner class for storing a single coordinate pair
     */
    public static class SlotCoordinate {
        public int x = 0;
        public int y = 0;
        
        public SlotCoordinate() {
            this(0, 0);
        }
        
        public SlotCoordinate(int x, int y) {
            this.x = x;
            this.y = y;
        }
        
        public boolean isMapped() {
            return x != 0 || y != 0;
        }
        
        @Override
        public String toString() {
            return String.format("(%d, %d)", x, y);
        }
    }
}

