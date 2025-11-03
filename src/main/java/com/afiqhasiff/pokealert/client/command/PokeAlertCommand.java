package com.afiqhasiff.pokealert.client.command;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.config.ConfigManager;
import com.afiqhasiff.pokealert.client.config.PokeAlertConfig;
import com.afiqhasiff.pokealert.client.util.PokemonLists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Client-side command handler for PokéAlert.
 * Provides /pokealert commands for mod configuration.
 */
public class PokeAlertCommand {
    
    private static final String[] CATEGORIES = {
        "legendaries", "mythics", "starters", "babies", 
        "ultrabeasts", "shinies", "paradox"
    };
    
    private static final String[] LIST_TYPES = {
        "legendaries", "mythics", "starters", "babies", 
        "ultrabeasts", "shinies", "paradox", "whitelist", "blacklist"
    };
    
    private static final SuggestionProvider<FabricClientCommandSource> CATEGORY_SUGGESTIONS = (context, builder) -> {
        for (String category : CATEGORIES) {
            builder.suggest(category);
        }
        return builder.buildFuture();
    };
    
    private static final SuggestionProvider<FabricClientCommandSource> LIST_SUGGESTIONS = (context, builder) -> {
        for (String type : LIST_TYPES) {
            builder.suggest(type);
        }
        return builder.buildFuture();
    };
    
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("pokealert")
                    .executes(context -> showHelp(context))
                    
                    // /pokealert enable
                    .then(ClientCommandManager.literal("enable")
                        .executes(context -> setModEnabled(context, true)))
                    
                    // /pokealert disable
                    .then(ClientCommandManager.literal("disable")
                        .executes(context -> setModEnabled(context, false)))
                    
                    // /pokealert status
                    .then(ClientCommandManager.literal("status")
                        .executes(context -> showStatus(context)))
                    
                    // /pokealert list <category/whitelist/blacklist>
                    .then(ClientCommandManager.literal("list")
                        .then(ClientCommandManager.argument("type", StringArgumentType.word())
                            .suggests(LIST_SUGGESTIONS)
                            .executes(context -> listPokemon(context))))
                    
                    // /pokealert categories <category> <enable/disable>
                    .then(ClientCommandManager.literal("categories")
                        .then(ClientCommandManager.argument("category", StringArgumentType.word())
                            .suggests(CATEGORY_SUGGESTIONS)
                            .then(ClientCommandManager.literal("enable")
                                .executes(context -> setCategory(context, true)))
                            .then(ClientCommandManager.literal("disable")
                                .executes(context -> setCategory(context, false)))))
                    
                    // /pokealert whitelist <add/remove> <pokemonName>
                    .then(ClientCommandManager.literal("whitelist")
                        .then(ClientCommandManager.literal("add")
                            .then(ClientCommandManager.argument("pokemon", StringArgumentType.greedyString())
                                .executes(context -> addToWhitelist(context))))
                        .then(ClientCommandManager.literal("remove")
                            .then(ClientCommandManager.argument("pokemon", StringArgumentType.greedyString())
                                .executes(context -> removeFromWhitelist(context))))
                        .then(ClientCommandManager.literal("list")
                            .executes(context -> listWhitelist(context))))
                    
                    // /pokealert blacklist <add/remove> <pokemonName>
                    .then(ClientCommandManager.literal("blacklist")
                        .then(ClientCommandManager.literal("add")
                            .then(ClientCommandManager.argument("pokemon", StringArgumentType.greedyString())
                                .executes(context -> addToBlacklist(context))))
                        .then(ClientCommandManager.literal("remove")
                            .then(ClientCommandManager.argument("pokemon", StringArgumentType.greedyString())
                                .executes(context -> removeFromBlacklist(context))))
                        .then(ClientCommandManager.literal("list")
                            .executes(context -> listBlacklist(context))))
                    
                    // /pokealert excludedworlds <add/remove> <worldName>
                    .then(ClientCommandManager.literal("excludedworlds")
                        .then(ClientCommandManager.literal("add")
                            .then(ClientCommandManager.argument("world", StringArgumentType.greedyString())
                                .executes(context -> addToExcludedWorlds(context))))
                        .then(ClientCommandManager.literal("remove")
                            .then(ClientCommandManager.argument("world", StringArgumentType.greedyString())
                                .executes(context -> removeFromExcludedWorlds(context))))
                        .then(ClientCommandManager.literal("list")
                            .executes(context -> listExcludedWorlds(context))))
                    
                    // /pokealert notifications <text/sound/telegram> <enable/disable>
                    .then(ClientCommandManager.literal("notifications")
                        .then(ClientCommandManager.literal("text")
                            .then(ClientCommandManager.literal("enable")
                                .executes(context -> setNotification(context, "text", true)))
                            .then(ClientCommandManager.literal("disable")
                                .executes(context -> setNotification(context, "text", false))))
                        .then(ClientCommandManager.literal("sound")
                            .then(ClientCommandManager.literal("enable")
                                .executes(context -> setNotification(context, "sound", true)))
                            .then(ClientCommandManager.literal("disable")
                                .executes(context -> setNotification(context, "sound", false))))
                        .then(ClientCommandManager.literal("telegram")
                            .then(ClientCommandManager.literal("enable")
                                .executes(context -> setNotification(context, "telegram", true)))
                            .then(ClientCommandManager.literal("disable")
                                .executes(context -> setNotification(context, "telegram", false)))))
            );
        });
    }
    
    private static int showHelp(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        
        // Use PokéAlert color scheme: Gray brackets, Red "PokeAlert", White text
        source.sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] ").formatted(Formatting.GRAY))
                .append(Text.literal("Commands").formatted(Formatting.WHITE))
        );
        
        source.sendFeedback(Text.literal("  /pokealert enable").formatted(Formatting.WHITE)
            .append(Text.literal(" - Enable the mod").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("  /pokealert disable").formatted(Formatting.WHITE)
            .append(Text.literal(" - Disable the mod").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("  /pokealert status").formatted(Formatting.WHITE)
            .append(Text.literal(" - Show current status").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("  /pokealert list <type>").formatted(Formatting.WHITE)
            .append(Text.literal(" - List Pokémon in category").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("  /pokealert categories <name> <enable/disable>").formatted(Formatting.WHITE)
            .append(Text.literal(" - Toggle category").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("  /pokealert whitelist <add/remove/list>").formatted(Formatting.WHITE)
            .append(Text.literal(" - Manage whitelist").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("  /pokealert blacklist <add/remove/list>").formatted(Formatting.WHITE)
            .append(Text.literal(" - Manage blacklist").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("  /pokealert excludedworlds <add/remove/list>").formatted(Formatting.WHITE)
            .append(Text.literal(" - Manage worlds").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("  /pokealert notifications <type> <enable/disable>").formatted(Formatting.WHITE)
            .append(Text.literal(" - Toggle notifications").formatted(Formatting.GRAY)));
        
        return 1;
    }
    
    private static int showStatus(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        PokeAlertConfig config = ConfigManager.getConfig();
        
        // Header with PokéAlert color scheme
        source.sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] ").formatted(Formatting.GRAY))
                .append(Text.literal("Status").formatted(Formatting.WHITE))
        );
        
        source.sendFeedback(Text.literal("  Mod: ").formatted(Formatting.GRAY)
            .append(Text.literal(config.modEnabled ? "ENABLED" : "DISABLED")
                .formatted(config.modEnabled ? Formatting.GREEN : Formatting.RED)));
        
        source.sendFeedback(Text.literal("  Categories:").formatted(Formatting.WHITE));
        source.sendFeedback(formatCategoryStatus("Legendaries", config.broadcastAllLegendaries));
        source.sendFeedback(formatCategoryStatus("Mythics", config.broadcastAllMythics));
        source.sendFeedback(formatCategoryStatus("Starters", config.broadcastAllStarter));
        source.sendFeedback(formatCategoryStatus("Babies", config.broadcastAllBabies));
        source.sendFeedback(formatCategoryStatus("Ultra Beasts", config.broadcastAllUltraBeasts));
        source.sendFeedback(formatCategoryStatus("Shinies", config.broadcastAllShinies));
        source.sendFeedback(formatCategoryStatus("Paradox", config.broadcastAllParadox));
        
        source.sendFeedback(Text.literal("  Notifications:").formatted(Formatting.WHITE));
        source.sendFeedback(formatCategoryStatus("In-Game Text", config.inGameTextEnabled));
        source.sendFeedback(formatCategoryStatus("In-Game Sound", config.inGameSoundEnabled));
        source.sendFeedback(formatCategoryStatus("Telegram", config.telegramEnabled));
        
        source.sendFeedback(Text.literal("  Whitelist: ").formatted(Formatting.GRAY)
            .append(Text.literal(config.broadcastWhitelist.length + " entries").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  Blacklist: ").formatted(Formatting.GRAY)
            .append(Text.literal(config.broadcastBlacklist.length + " entries").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  Excluded Worlds: ").formatted(Formatting.GRAY)
            .append(Text.literal(config.excludedWorlds.length + " entries").formatted(Formatting.WHITE)));
        
        return 1;
    }
    
    private static Text formatCategoryStatus(String name, boolean enabled) {
        return Text.literal("    • " + name + ": ").formatted(Formatting.GRAY)
            .append(Text.literal(enabled ? "ON" : "OFF")
                .formatted(enabled ? Formatting.GREEN : Formatting.RED));
    }
    
    private static int listPokemon(CommandContext<FabricClientCommandSource> context) {
        String type = StringArgumentType.getString(context, "type").toLowerCase();
        FabricClientCommandSource source = context.getSource();
        PokeAlertConfig config = ConfigManager.getConfig();
        
        String[] pokemonList = null;
        String displayName = "";
        Formatting categoryColor = Formatting.WHITE;
        
        switch (type) {
            case "legendaries" -> {
                pokemonList = PokemonLists.legendaries;
                displayName = "Legendary Pokémon";
                categoryColor = Formatting.GOLD;
            }
            case "mythics" -> {
                pokemonList = PokemonLists.mythics;
                displayName = "Mythical Pokémon";
                categoryColor = Formatting.DARK_PURPLE;
            }
            case "starters" -> {
                pokemonList = PokemonLists.starter;
                displayName = "Starter Pokémon";
                categoryColor = Formatting.GREEN;
            }
            case "babies" -> {
                pokemonList = PokemonLists.babies;
                displayName = "Baby Pokémon";
                categoryColor = Formatting.AQUA;
            }
            case "ultrabeasts" -> {
                pokemonList = PokemonLists.ultra_beasts;
                displayName = "Ultra Beasts";
                categoryColor = Formatting.DARK_AQUA;
            }
            case "paradox" -> {
                pokemonList = PokemonLists.paradox_mons;
                displayName = "Paradox Pokémon";
                categoryColor = Formatting.DARK_RED;
            }
            case "shinies" -> {
                source.sendFeedback(
                    Text.literal("[").formatted(Formatting.GRAY)
                        .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                        .append(Text.literal("] ").formatted(Formatting.GRAY))
                        .append(Text.literal("Shiny Pokémon includes all Pokémon when shiny").formatted(Formatting.LIGHT_PURPLE))
                );
                return 1;
            }
            case "whitelist" -> {
                pokemonList = config.broadcastWhitelist;
                displayName = "Custom Whitelist";
                categoryColor = Formatting.GREEN;
            }
            case "blacklist" -> {
                pokemonList = config.broadcastBlacklist;
                displayName = "Custom Blacklist";
                categoryColor = Formatting.RED;
            }
            default -> {
                source.sendError(Text.literal("Unknown list type: " + type));
                return 0;
            }
        }
        
        // Header
        source.sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] ").formatted(Formatting.GRAY))
                .append(Text.literal(displayName).formatted(categoryColor))
                .append(Text.literal(" (").formatted(Formatting.GRAY))
                .append(Text.literal(String.valueOf(pokemonList.length)).formatted(Formatting.WHITE))
                .append(Text.literal(")").formatted(Formatting.GRAY))
        );
        
        // List Pokemon
        if (pokemonList.length == 0) {
            source.sendFeedback(Text.literal("  Empty list").formatted(Formatting.GRAY));
        } else if (pokemonList.length <= 20) {
            // Show all if 20 or less
            for (String pokemon : pokemonList) {
                source.sendFeedback(Text.literal("  • ").formatted(Formatting.GRAY)
                    .append(Text.literal(formatPokemonName(pokemon)).formatted(Formatting.WHITE)));
            }
        } else {
            // Show first 15 and last 5 if more than 20
            for (int i = 0; i < 15; i++) {
                source.sendFeedback(Text.literal("  • ").formatted(Formatting.GRAY)
                    .append(Text.literal(formatPokemonName(pokemonList[i])).formatted(Formatting.WHITE)));
            }
            source.sendFeedback(Text.literal("  ... and ").formatted(Formatting.GRAY)
                .append(Text.literal(String.valueOf(pokemonList.length - 20)).formatted(Formatting.WHITE))
                .append(Text.literal(" more").formatted(Formatting.GRAY)));
            for (int i = pokemonList.length - 5; i < pokemonList.length; i++) {
                source.sendFeedback(Text.literal("  • ").formatted(Formatting.GRAY)
                    .append(Text.literal(formatPokemonName(pokemonList[i])).formatted(Formatting.WHITE)));
            }
        }
        
        return 1;
    }
    
    private static String formatPokemonName(String name) {
        // Capitalize first letter of each word
        String[] parts = name.split("[ -]");
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                formatted.append(name.contains("-") ? "-" : " ");
            }
            if (!parts[i].isEmpty()) {
                formatted.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) {
                    formatted.append(parts[i].substring(1));
                }
            }
        }
        return formatted.toString();
    }
    
    private static int setModEnabled(CommandContext<FabricClientCommandSource> context, boolean enabled) {
        PokeAlertConfig config = ConfigManager.getConfig();
        config.modEnabled = enabled;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] ").formatted(Formatting.GRAY))
                .append(Text.literal("Mod has been ").formatted(Formatting.WHITE))
                .append(Text.literal(enabled ? "ENABLED" : "DISABLED")
                    .formatted(enabled ? Formatting.GREEN : Formatting.RED))
        );
        
        return 1;
    }
    
    private static int setCategory(CommandContext<FabricClientCommandSource> context, boolean enabled) {
        String category = StringArgumentType.getString(context, "category").toLowerCase();
        PokeAlertConfig config = ConfigManager.getConfig();
        boolean updated = true;
        
        switch (category) {
            case "legendaries" -> config.broadcastAllLegendaries = enabled;
            case "mythics" -> config.broadcastAllMythics = enabled;
            case "starters" -> config.broadcastAllStarter = enabled;
            case "babies" -> config.broadcastAllBabies = enabled;
            case "ultrabeasts" -> config.broadcastAllUltraBeasts = enabled;
            case "shinies" -> config.broadcastAllShinies = enabled;
            case "paradox" -> config.broadcastAllParadox = enabled;
            default -> {
                context.getSource().sendError(Text.literal("Unknown category: " + category));
                return 0;
            }
        }
        
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] ").formatted(Formatting.GRAY))
                .append(Text.literal(Character.toUpperCase(category.charAt(0)) + category.substring(1)).formatted(Formatting.WHITE))
                .append(Text.literal(" has been ").formatted(Formatting.GRAY))
                .append(Text.literal(enabled ? "ENABLED" : "DISABLED")
                    .formatted(enabled ? Formatting.GREEN : Formatting.RED))
        );
        
        return 1;
    }
    
    private static int setNotification(CommandContext<FabricClientCommandSource> context, String type, boolean enabled) {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        switch (type) {
            case "text" -> config.inGameTextEnabled = enabled;
            case "sound" -> config.inGameSoundEnabled = enabled;
            case "telegram" -> config.telegramEnabled = enabled;
        }
        
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] ").formatted(Formatting.GRAY))
                .append(Text.literal(type.substring(0, 1).toUpperCase() + type.substring(1)).formatted(Formatting.WHITE))
                .append(Text.literal(" notifications ").formatted(Formatting.GRAY))
                .append(Text.literal(enabled ? "ENABLED" : "DISABLED")
                    .formatted(enabled ? Formatting.GREEN : Formatting.RED))
        );
        
        return 1;
    }
    
    private static int addToWhitelist(CommandContext<FabricClientCommandSource> context) {
        String pokemon = StringArgumentType.getString(context, "pokemon");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        List<String> whitelist = new ArrayList<>(Arrays.asList(config.broadcastWhitelist));
        if (!whitelist.contains(pokemon)) {
            whitelist.add(pokemon);
            config.broadcastWhitelist = whitelist.toArray(new String[0]);
            ConfigManager.updateConfig(config);
            PokeAlertClient.getInstance().reloadConfig();
            
            context.getSource().sendFeedback(
                Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                    .append(Text.literal("] Added ").formatted(Formatting.GRAY))
                    .append(Text.literal(pokemon).formatted(Formatting.WHITE))
                    .append(Text.literal(" to whitelist").formatted(Formatting.GRAY))
            );
        } else {
            context.getSource().sendError(Text.literal(pokemon + " is already in the whitelist"));
        }
        
        return 1;
    }
    
    private static int removeFromWhitelist(CommandContext<FabricClientCommandSource> context) {
        String pokemon = StringArgumentType.getString(context, "pokemon");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        List<String> whitelist = new ArrayList<>(Arrays.asList(config.broadcastWhitelist));
        if (whitelist.remove(pokemon)) {
            config.broadcastWhitelist = whitelist.toArray(new String[0]);
            ConfigManager.updateConfig(config);
            PokeAlertClient.getInstance().reloadConfig();
            
            context.getSource().sendFeedback(
                Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                    .append(Text.literal("] Removed ").formatted(Formatting.GRAY))
                    .append(Text.literal(pokemon).formatted(Formatting.WHITE))
                    .append(Text.literal(" from whitelist").formatted(Formatting.GRAY))
            );
        } else {
            context.getSource().sendError(Text.literal(pokemon + " is not in the whitelist"));
        }
        
        return 1;
    }
    
    private static int listWhitelist(CommandContext<FabricClientCommandSource> context) {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Whitelist ").formatted(Formatting.GRAY))
                .append(Text.literal("(" + config.broadcastWhitelist.length + ")").formatted(Formatting.WHITE))
        );
        
        if (config.broadcastWhitelist.length == 0) {
            context.getSource().sendFeedback(Text.literal("  Empty list").formatted(Formatting.GRAY));
        } else {
            for (String pokemon : config.broadcastWhitelist) {
                context.getSource().sendFeedback(Text.literal("  • ").formatted(Formatting.GRAY)
                    .append(Text.literal(pokemon).formatted(Formatting.WHITE)));
            }
        }
        
        return 1;
    }
    
    private static int addToBlacklist(CommandContext<FabricClientCommandSource> context) {
        String pokemon = StringArgumentType.getString(context, "pokemon");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        List<String> blacklist = new ArrayList<>(Arrays.asList(config.broadcastBlacklist));
        if (!blacklist.contains(pokemon)) {
            blacklist.add(pokemon);
            config.broadcastBlacklist = blacklist.toArray(new String[0]);
            ConfigManager.updateConfig(config);
            PokeAlertClient.getInstance().reloadConfig();
            
            context.getSource().sendFeedback(
                Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                    .append(Text.literal("] Added ").formatted(Formatting.GRAY))
                    .append(Text.literal(pokemon).formatted(Formatting.WHITE))
                    .append(Text.literal(" to blacklist").formatted(Formatting.GRAY))
            );
        } else {
            context.getSource().sendError(Text.literal(pokemon + " is already in the blacklist"));
        }
        
        return 1;
    }
    
    private static int removeFromBlacklist(CommandContext<FabricClientCommandSource> context) {
        String pokemon = StringArgumentType.getString(context, "pokemon");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        List<String> blacklist = new ArrayList<>(Arrays.asList(config.broadcastBlacklist));
        if (blacklist.remove(pokemon)) {
            config.broadcastBlacklist = blacklist.toArray(new String[0]);
            ConfigManager.updateConfig(config);
            PokeAlertClient.getInstance().reloadConfig();
            
            context.getSource().sendFeedback(
                Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                    .append(Text.literal("] Removed ").formatted(Formatting.GRAY))
                    .append(Text.literal(pokemon).formatted(Formatting.WHITE))
                    .append(Text.literal(" from blacklist").formatted(Formatting.GRAY))
            );
        } else {
            context.getSource().sendError(Text.literal(pokemon + " is not in the blacklist"));
        }
        
        return 1;
    }
    
    private static int listBlacklist(CommandContext<FabricClientCommandSource> context) {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Blacklist ").formatted(Formatting.GRAY))
                .append(Text.literal("(" + config.broadcastBlacklist.length + ")").formatted(Formatting.WHITE))
        );
        
        if (config.broadcastBlacklist.length == 0) {
            context.getSource().sendFeedback(Text.literal("  Empty list").formatted(Formatting.GRAY));
        } else {
            for (String pokemon : config.broadcastBlacklist) {
                context.getSource().sendFeedback(Text.literal("  • ").formatted(Formatting.GRAY)
                    .append(Text.literal(pokemon).formatted(Formatting.WHITE)));
            }
        }
        
        return 1;
    }
    
    private static int addToExcludedWorlds(CommandContext<FabricClientCommandSource> context) {
        String world = StringArgumentType.getString(context, "world");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        List<String> excludedWorlds = new ArrayList<>(Arrays.asList(config.excludedWorlds));
        if (!excludedWorlds.contains(world)) {
            excludedWorlds.add(world);
            config.excludedWorlds = excludedWorlds.toArray(new String[0]);
            ConfigManager.updateConfig(config);
            
            context.getSource().sendFeedback(
                Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                    .append(Text.literal("] Added ").formatted(Formatting.GRAY))
                    .append(Text.literal(world).formatted(Formatting.WHITE))
                    .append(Text.literal(" to excluded worlds").formatted(Formatting.GRAY))
            );
        } else {
            context.getSource().sendError(Text.literal(world + " is already excluded"));
        }
        
        return 1;
    }
    
    private static int removeFromExcludedWorlds(CommandContext<FabricClientCommandSource> context) {
        String world = StringArgumentType.getString(context, "world");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        List<String> excludedWorlds = new ArrayList<>(Arrays.asList(config.excludedWorlds));
        if (excludedWorlds.remove(world)) {
            config.excludedWorlds = excludedWorlds.toArray(new String[0]);
            ConfigManager.updateConfig(config);
            
            context.getSource().sendFeedback(
                Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                    .append(Text.literal("] Removed ").formatted(Formatting.GRAY))
                    .append(Text.literal(world).formatted(Formatting.WHITE))
                    .append(Text.literal(" from excluded worlds").formatted(Formatting.GRAY))
            );
        } else {
            context.getSource().sendError(Text.literal(world + " is not excluded"));
        }
        
        return 1;
    }
    
    private static int listExcludedWorlds(CommandContext<FabricClientCommandSource> context) {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Excluded Worlds ").formatted(Formatting.GRAY))
                .append(Text.literal("(" + config.excludedWorlds.length + ")").formatted(Formatting.WHITE))
        );
        
        if (config.excludedWorlds.length == 0) {
            context.getSource().sendFeedback(Text.literal("  No worlds excluded").formatted(Formatting.GRAY));
        } else {
            for (String world : config.excludedWorlds) {
                context.getSource().sendFeedback(Text.literal("  • ").formatted(Formatting.GRAY)
                    .append(Text.literal(world).formatted(Formatting.WHITE)));
            }
        }
        
        return 1;
    }
}
