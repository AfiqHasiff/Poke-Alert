package com.afiqhasiff.pokealert.client.notification;

import com.afiqhasiff.pokealert.client.util.RarityScraper;
import net.minecraft.entity.Entity;

/**
 * Data object containing information about a detected Pokemon spawn.
 * This is passed to notification services to format and send notifications.
 */
public class PokemonSpawnData {
    private final String pokemonName;
    private final boolean isShiny;
    private final int x;
    private final int y;
    private final int z;
    private final String worldName;
    private final long timestamp;
    private RarityScraper.RarityInfo rarityInfo;

    public PokemonSpawnData(String pokemonName, boolean isShiny, int x, int y, int z, String worldName) {
        this.pokemonName = pokemonName;
        this.isShiny = isShiny;
        this.x = x;
        this.y = y;
        this.z = z;
        this.worldName = worldName;
        this.timestamp = System.currentTimeMillis();
    }

    public static PokemonSpawnData fromEntity(Entity entity, String pokemonName, boolean isShiny, String worldName) {
        return new PokemonSpawnData(
            pokemonName,
            isShiny,
            (int) entity.getX(),
            (int) entity.getY(),
            (int) entity.getZ(),
            worldName
        );
    }

    // Getters
    public String getPokemonName() {
        return pokemonName;
    }

    public boolean isShiny() {
        return isShiny;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getWorldName() {
        return worldName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFormattedName() {
        return isShiny ? "Shiny " + pokemonName : pokemonName;
    }

    public String getFormattedLocation() {
        return String.format("X: %d, Y: %d, Z: %d", x, y, z);
    }
    
    /**
     * Get the rarity info for this Pokemon.
     * Caches the result after first scrape.
     */
    public RarityScraper.RarityInfo getRarityInfo() {
        if (rarityInfo == null) {
            rarityInfo = RarityScraper.getPokemonRarity(pokemonName);
        }
        return rarityInfo;
    }
    
    /**
     * Get just the rarity text
     */
    public String getRarityText() {
        return getRarityInfo().getName();
    }
    
    /**
     * Get the Bulbapedia URL for this Pokemon
     */
    public String getBulbapediaUrl() {
        return RarityScraper.generateBulbapediaUrl(pokemonName);
    }

    @Override
    public String toString() {
        return String.format("PokemonSpawnData{name='%s', shiny=%b, location=(%d,%d,%d), world='%s'}",
            pokemonName, isShiny, x, y, z, worldName);
    }
}

