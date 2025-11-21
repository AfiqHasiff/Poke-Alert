package com.afiqhasiff.pokealert.client.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.afiqhasiff.pokealert.client.util.PokemonLists;

public class PokeAlertConfig {
    // Master toggle
    public boolean modEnabled = true;
    
    // Detection categories
    public boolean broadcastAllLegendaries = true;
    public boolean broadcastAllMythics = true;
    public boolean broadcastAllStarter = false;
    public boolean broadcastAllBabies = false;
    public boolean broadcastAllUltraBeasts = false;
    public boolean broadcastAllShinies = true;
    public boolean broadcastAllParadox = false;
    
    // Custom whitelist and blacklist
    public String[] broadcastWhitelist = {"Mew", "Mewtwo"};
    public String[] broadcastBlacklist = new String[0];
    
    // Notification toggles
    public boolean inGameTextEnabled = true;
    public boolean inGameSoundEnabled = true;
    public float inGameSoundVolume = 1.0f; // 0.0 to 1.0 (0% to 100%)
    public boolean telegramEnabled = true;
    
    // World exclusion list (users can input "spawn" or "minecraft:spawn")
    public String[] excludedWorlds = {"spawn"};
    
    // Egg timer settings
    public int eggTimerDuration = 30; // Default 30 minutes
    public boolean eggTimerTextNotification = true;
    public boolean eggTimerTelegramNotification = true;
    
    // Realm Manager Automation Settings
    public boolean realmManagerEnabled = true;
    public int antiAfkKeybind = 329; // Default: Numpad 9 (GLFW.GLFW_KEY_KP_9)
    public String realmReturnCommand = "/home new"; // Default command to return to main realm

    public String[] getCombinedWhitelist(){
        List<String> combinedList = new ArrayList<String>();

        if (this.broadcastAllLegendaries) {
            combinedList.addAll(new ArrayList<String>(Arrays.asList(PokemonLists.legendaries)));
        }
        if (this.broadcastAllMythics) {
            combinedList.addAll(new ArrayList<String>(Arrays.asList(PokemonLists.mythics)));
        }
        if (this.broadcastAllStarter) {
            combinedList.addAll(new ArrayList<String>(Arrays.asList(PokemonLists.starter)));
        }
        if (this.broadcastAllBabies) {
            combinedList.addAll(new ArrayList<String>(Arrays.asList(PokemonLists.babies)));
        }
        if (this.broadcastAllUltraBeasts) {
            combinedList.addAll(new ArrayList<String>(Arrays.asList(PokemonLists.ultra_beasts)));
        }
        if (this.broadcastAllParadox) {
            combinedList.addAll(new ArrayList<String>(Arrays.asList(PokemonLists.paradox_mons)));
        }

        combinedList.addAll(new ArrayList<String>(Arrays.asList(this.broadcastWhitelist)));

        // turn everything lowercase just in case
        for (int i = 0; i < combinedList.size(); i++) {
            combinedList.set(i, combinedList.get(i).toLowerCase());
        }

        return combinedList.toArray(new String[combinedList.size()]);
    }
    
    /**
     * Check if a Pokemon should trigger notification
     * @param pokemonName The name of the Pokemon to check
     * @return true if Pokemon should trigger notification
     */
    public boolean shouldNotify(String pokemonName) {
        if (!modEnabled) {
            return false;
        }
        
        String lowerName = pokemonName.toLowerCase();
        
        // Check blacklist first
        for (String blacklisted : broadcastBlacklist) {
            if (blacklisted.toLowerCase().equals(lowerName)) {
                return false;
            }
        }
        
        // Check whitelist
        String[] whitelist = getCombinedWhitelist();
        for (String whitelisted : whitelist) {
            if (whitelisted.equals(lowerName)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if a world is excluded from notifications
     * @param worldName The name of the world to check
     * @return true if world is excluded
     */
    public boolean isWorldExcluded(String worldName) {
        for (String excluded : excludedWorlds) {
            // Handle both formats: "spawn" and "minecraft:spawn"
            String normalizedExcluded = excluded.contains(":") ? excluded : "minecraft:" + excluded;
            String normalizedWorld = worldName.contains(":") ? worldName : "minecraft:" + worldName;
            
            if (normalizedExcluded.equalsIgnoreCase(normalizedWorld)) {
                return true;
            }
        }
        return false;
    }
}
