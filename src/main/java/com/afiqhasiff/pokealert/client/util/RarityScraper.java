package com.afiqhasiff.pokealert.client.util;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import net.minecraft.util.Formatting;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for scraping Pokemon rarity from Bulbapedia.
 * Provides caching and fallback mechanisms.
 */
public class RarityScraper {
    // Cache for scraped rarities to avoid repeated requests
    private static final ConcurrentHashMap<String, RarityInfo> rarityCache = new ConcurrentHashMap<>();
    
    /**
     * Data class containing rarity information
     */
    public static class RarityInfo {
        private final String name;
        private final Formatting color;
        
        public RarityInfo(String name, Formatting color) {
            this.name = name;
            this.color = color;
        }
        
        public String getName() {
            return name;
        }
        
        public Formatting getColor() {
            return color;
        }
    }
    
    /**
     * Get Pokemon rarity with color formatting.
     * Tries to scrape from Bulbapedia first, then falls back to predefined lists.
     * 
     * @param pokemonName The name of the Pokemon
     * @return RarityInfo containing rarity name and color
     */
    public static RarityInfo getPokemonRarity(String pokemonName) {
        String lowerName = pokemonName.toLowerCase().replace(" ", "-");
        
        // Check cache first
        RarityInfo cached = rarityCache.get(lowerName);
        if (cached != null) {
            return cached;
        }
        
        // Try to scrape from Bulbapedia
        String scrapedRarity = scrapeBulbapediaRarity(pokemonName);
        
        // Map scraped rarity to RarityInfo
        RarityInfo rarityInfo = mapRarityToInfo(scrapedRarity, lowerName);
        
        // Cache the result
        rarityCache.put(lowerName, rarityInfo);
        
        return rarityInfo;
    }
    
    /**
     * Get rarity as a simple string (for Telegram notifications)
     */
    public static String getPokemonRarityText(String pokemonName) {
        RarityInfo info = getPokemonRarity(pokemonName);
        return info.getName();
    }
    
    /**
     * Generate Bulbapedia URL for a Pokemon
     */
    public static String generateBulbapediaUrl(String pokemonName) {
        try {
            // Capitalize first letter
            String formattedName = pokemonName.substring(0, 1).toUpperCase() + pokemonName.substring(1).toLowerCase();
            // URL encode the name
            String encodedName = URLEncoder.encode(formattedName, StandardCharsets.UTF_8);
            return String.format("https://bulbapedia.bulbagarden.net/wiki/%s_(Pokémon)", encodedName);
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("Failed to generate Bulbapedia URL for: {}", pokemonName, e);
            return "https://bulbapedia.bulbagarden.net/wiki/List_of_Pokémon_by_National_Pokédex_number";
        }
    }
    
    /**
     * Scrape rarity information from Bulbapedia
     */
    private static String scrapeBulbapediaRarity(String pokemonName) {
        String url = generateBulbapediaUrl(pokemonName);
        
        try {
            PokeAlertClient.LOGGER.debug("Scraping rarity for {} from {}", pokemonName, url);
            
            // Fetch the page with timeout
            Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(5000)
                .get();
            
            // Look for rarity in the page
            // Pattern: <td>Rarity: Rare</td>
            for (Element element : doc.select("td")) {
                String text = element.text();
                if (text.startsWith("Rarity:")) {
                    String rarity = text.substring(8).trim(); // Remove "Rarity: " prefix
                    PokeAlertClient.LOGGER.debug("Found rarity for {}: {}", pokemonName, rarity);
                    return rarity;
                }
            }
            
            // Check for legendary/mythical status in categories
            for (Element element : doc.select("a[title*='Category:']")) {
                String title = element.attr("title");
                if (title.contains("Legendary Pokémon")) {
                    return "Legendary";
                } else if (title.contains("Mythical Pokémon")) {
                    return "Mythical";
                } else if (title.contains("Ultra Beasts")) {
                    return "Ultra Beast";
                } else if (title.contains("Paradox Pokémon")) {
                    return "Paradox";
                }
            }
            
            PokeAlertClient.LOGGER.warn("Rarity not found for {} on Bulbapedia, using fallback", pokemonName);
            return null; // Will trigger fallback
            
        } catch (IOException e) {
            PokeAlertClient.LOGGER.error("Failed to scrape rarity for {}: {}", pokemonName, e.getMessage());
            return null; // Will trigger fallback
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("Unexpected error scraping rarity for {}: {}", pokemonName, e.getMessage());
            return null; // Will trigger fallback
        }
    }
    
    /**
     * Map rarity string to RarityInfo with color
     */
    private static RarityInfo mapRarityToInfo(String scrapedRarity, String pokemonNameLower) {
        // If scraping succeeded, map to appropriate color
        if (scrapedRarity != null && !scrapedRarity.isEmpty()) {
            String rarityLower = scrapedRarity.toLowerCase();
            
            if (rarityLower.contains("legendary")) {
                return new RarityInfo("Legendary", Formatting.GOLD);
            } else if (rarityLower.contains("mythical")) {
                return new RarityInfo("Mythical", Formatting.DARK_PURPLE);
            } else if (rarityLower.contains("ultra beast")) {
                return new RarityInfo("Ultra Beast", Formatting.DARK_AQUA);
            } else if (rarityLower.contains("paradox")) {
                return new RarityInfo("Paradox", Formatting.DARK_RED);
            } else if (rarityLower.contains("rare") || rarityLower.contains("very rare")) {
                return new RarityInfo(scrapedRarity, Formatting.YELLOW);
            } else if (rarityLower.contains("uncommon")) {
                return new RarityInfo(scrapedRarity, Formatting.GREEN);
            } else if (rarityLower.contains("common")) {
                return new RarityInfo(scrapedRarity, Formatting.GRAY);
            } else {
                // Use the scraped value as-is with a default color
                return new RarityInfo(scrapedRarity, Formatting.WHITE);
            }
        }
        
        // Fallback to predefined lists if scraping failed
        if (Arrays.asList(PokemonLists.legendaries).contains(pokemonNameLower)) {
            return new RarityInfo("Legendary", Formatting.GOLD);
        } else if (Arrays.asList(PokemonLists.mythics).contains(pokemonNameLower)) {
            return new RarityInfo("Mythical", Formatting.DARK_PURPLE);
        } else if (Arrays.asList(PokemonLists.ultra_beasts).contains(pokemonNameLower)) {
            return new RarityInfo("Ultra Beast", Formatting.DARK_AQUA);
        } else if (Arrays.asList(PokemonLists.paradox_mons).contains(pokemonNameLower)) {
            return new RarityInfo("Paradox", Formatting.DARK_RED);
        } else if (Arrays.asList(PokemonLists.starter).contains(pokemonNameLower)) {
            return new RarityInfo("Starter", Formatting.GREEN);
        } else if (Arrays.asList(PokemonLists.babies).contains(pokemonNameLower)) {
            return new RarityInfo("Baby", Formatting.AQUA);
        } else {
            // Default fallback
            return new RarityInfo("Wild", Formatting.GRAY);
        }
    }
    
    /**
     * Clear the rarity cache (useful for testing or refresh)
     */
    public static void clearCache() {
        rarityCache.clear();
    }
}
