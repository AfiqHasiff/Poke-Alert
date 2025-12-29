package com.afiqhasiff.pokealert.client.command;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.config.ConfigManager;
import com.afiqhasiff.pokealert.client.config.PokeAlertConfig;
import com.afiqhasiff.pokealert.client.util.PokemonLists;
import com.afiqhasiff.pokealert.client.notification.EggTimerManager;
import com.afiqhasiff.pokealert.client.automation.EggHatcher;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
    // Confirmation tracking for redundant whitelist/blacklist additions
    private static String lastRedundantWhitelistPokemon = null;
    private static long lastRedundantWhitelistTime = 0;
    private static String lastRedundantBlacklistPokemon = null;
    private static long lastRedundantBlacklistTime = 0;
    private static final long CONFIRMATION_WINDOW = 10000; // 10 seconds
    
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
                    
                    // /pokealert help
                    .then(ClientCommandManager.literal("help")
                        .executes(context -> showHelp(context)))
                    
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
                    
                    // /pokealert eggtimer commands
                    .then(ClientCommandManager.literal("eggtimer")
                        .then(ClientCommandManager.literal("start")
                            .executes(context -> startEggTimer(context))
                            .then(ClientCommandManager.argument("minutes", IntegerArgumentType.integer(1, 120))
                                .executes(context -> startEggTimer(context, IntegerArgumentType.getInteger(context, "minutes")))))
                        .then(ClientCommandManager.literal("stop")
                            .executes(context -> stopEggTimer(context)))
                        .then(ClientCommandManager.literal("status")
                            .executes(context -> getEggTimerStatus(context)))
                        .then(ClientCommandManager.literal("duration")
                            .then(ClientCommandManager.argument("minutes", IntegerArgumentType.integer(1, 120))
                                .executes(context -> setEggTimerDuration(context)))))
                    // Egg hatcher commands
                    .then(ClientCommandManager.literal("realm")
                        .executes(context -> checkRealmStatus(context))
                        .then(ClientCommandManager.literal("toggle")
                            .executes(context -> toggleEggHatcher(context)))
                        .then(ClientCommandManager.literal("status")
                            .executes(context -> checkRealmStatus(context))))
                    
                    // Egg timer toggle command for parity
                    .then(ClientCommandManager.literal("timer")
                        .executes(context -> toggleEggTimer(context)))
                    
                    // ========== Phase 1: High Priority Missing Commands ==========
                    
                    // /pokealert blacklistchar <character>
                    .then(ClientCommandManager.literal("blacklistchar")
                        .then(ClientCommandManager.argument("character", StringArgumentType.string())
                            .executes(context -> setBlacklistCharacter(context))))
                    
                    // /pokealert soundvolume <0-100>
                    .then(ClientCommandManager.literal("soundvolume")
                        .then(ClientCommandManager.argument("volume", IntegerArgumentType.integer(0, 100))
                            .executes(context -> setSoundVolume(context))))
                    
                    // /pokealert eggtimer notifications <text/telegram> <enable/disable>
                    .then(ClientCommandManager.literal("eggtimer")
                        .then(ClientCommandManager.literal("notifications")
                            .then(ClientCommandManager.literal("text")
                                .then(ClientCommandManager.literal("enable")
                                    .executes(context -> setEggTimerNotification(context, "text", true)))
                                .then(ClientCommandManager.literal("disable")
                                    .executes(context -> setEggTimerNotification(context, "text", false))))
                            .then(ClientCommandManager.literal("telegram")
                                .then(ClientCommandManager.literal("enable")
                                    .executes(context -> setEggTimerNotification(context, "telegram", true)))
                                .then(ClientCommandManager.literal("disable")
                                    .executes(context -> setEggTimerNotification(context, "telegram", false))))))
                    
                    // /pokealert realm command <command>
                    .then(ClientCommandManager.literal("realm")
                        .then(ClientCommandManager.literal("command")
                            .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                                .executes(context -> setRealmReturnCommand(context)))))
                    
                    // /pokealert dm <enable/disable/status>
                    .then(ClientCommandManager.literal("dm")
                        .executes(context -> showDmStatus(context))
                        .then(ClientCommandManager.literal("enable")
                            .executes(context -> setDmDetection(context, true)))
                        .then(ClientCommandManager.literal("disable")
                            .executes(context -> setDmDetection(context, false)))
                        .then(ClientCommandManager.literal("status")
                            .executes(context -> showDmStatus(context)))
                        .then(ClientCommandManager.literal("notifications")
                            .then(ClientCommandManager.literal("telegram")
                                .then(ClientCommandManager.literal("enable")
                                    .executes(context -> setDmNotification(context, "telegram", true)))
                                .then(ClientCommandManager.literal("disable")
                                    .executes(context -> setDmNotification(context, "telegram", false))))
                            .then(ClientCommandManager.literal("ingame")
                                .then(ClientCommandManager.literal("enable")
                                    .executes(context -> setDmNotification(context, "ingame", true)))
                                .then(ClientCommandManager.literal("disable")
                                    .executes(context -> setDmNotification(context, "ingame", false))))))
                    
                    // /pokealert telegram <token/chatid/apiurl/ratelimit/cooldown/status/test>
                    .then(ClientCommandManager.literal("telegram")
                        .executes(context -> showTelegramStatus(context))
                        .then(ClientCommandManager.literal("status")
                            .executes(context -> showTelegramStatus(context)))
                        .then(ClientCommandManager.literal("token")
                            .then(ClientCommandManager.argument("token", StringArgumentType.greedyString())
                                .executes(context -> setTelegramToken(context))))
                        .then(ClientCommandManager.literal("chatid")
                            .then(ClientCommandManager.argument("chatId", StringArgumentType.greedyString())
                                .executes(context -> setTelegramChatId(context))))
                        .then(ClientCommandManager.literal("apiurl")
                            .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                                .executes(context -> setTelegramApiUrl(context))))
                        .then(ClientCommandManager.literal("ratelimit")
                            .then(ClientCommandManager.argument("maxPerMinute", IntegerArgumentType.integer(1, 60))
                                .executes(context -> setTelegramRateLimit(context))))
                        .then(ClientCommandManager.literal("cooldown")
                            .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(1, 300))
                                .executes(context -> setTelegramCooldown(context))))
                        .then(ClientCommandManager.literal("test")
                            .executes(context -> testTelegramConnection(context))))
                    
                    // ========== Phase 2: Anti-AFK Commands ==========
                    
                    // /pokealert antiafk region <set/get/reset>
                    .then(ClientCommandManager.literal("antiafk")
                        .then(ClientCommandManager.literal("region")
                            .executes(context -> showAntiAfkRegion(context))
                            .then(ClientCommandManager.literal("get")
                                .executes(context -> showAntiAfkRegion(context)))
                            .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("x1", IntegerArgumentType.integer())
                                    .then(ClientCommandManager.argument("z1", IntegerArgumentType.integer())
                                        .then(ClientCommandManager.argument("x2", IntegerArgumentType.integer())
                                            .then(ClientCommandManager.argument("z2", IntegerArgumentType.integer())
                                                .executes(context -> setAntiAfkRegion(context)))))))
                            .then(ClientCommandManager.literal("reset")
                                .executes(context -> resetAntiAfkRegion(context))))
                        
                        // /pokealert antiafk threshold <arrival/teleport> <blocks>
                        .then(ClientCommandManager.literal("threshold")
                            .then(ClientCommandManager.literal("arrival")
                                .then(ClientCommandManager.argument("blocks", IntegerArgumentType.integer(1, 10))
                                    .executes(context -> setArrivalThreshold(context))))
                            .then(ClientCommandManager.literal("teleport")
                                .then(ClientCommandManager.argument("blocks", IntegerArgumentType.integer(5, 50))
                                    .executes(context -> setTeleportDetectionOffset(context)))))
                        
                        // /pokealert antiafk timing <coordinate/realmspawn/realmoverworld/playermonitor/timeout>
                        .then(ClientCommandManager.literal("timing")
                            .executes(context -> showTimingSettings(context))
                            .then(ClientCommandManager.literal("coordinate")
                                .then(ClientCommandManager.argument("ms", IntegerArgumentType.integer(100, 2000))
                                    .executes(context -> setCoordinateCheckInterval(context))))
                            .then(ClientCommandManager.literal("realmspawn")
                                .then(ClientCommandManager.argument("ms", IntegerArgumentType.integer(100, 5000))
                                    .executes(context -> setRealmCheckIntervalSpawn(context))))
                            .then(ClientCommandManager.literal("realmoverworld")
                                .then(ClientCommandManager.argument("ms", IntegerArgumentType.integer(5000, 60000))
                                    .executes(context -> setRealmCheckIntervalOverworld(context))))
                            .then(ClientCommandManager.literal("playermonitor")
                                .then(ClientCommandManager.argument("ms", IntegerArgumentType.integer(1000, 30000))
                                    .executes(context -> setPlayerMonitorInterval(context))))
                            .then(ClientCommandManager.literal("timeout")
                                .then(ClientCommandManager.argument("ms", IntegerArgumentType.integer(10000, 120000))
                                    .executes(context -> setLocationTimeout(context))))
                            .then(ClientCommandManager.literal("reset")
                                .executes(context -> resetTimingSettings(context))))
                        
                        // /pokealert antiafk safety <monitoring/nearby/radius>
                        .then(ClientCommandManager.literal("safety")
                            .then(ClientCommandManager.literal("monitoring")
                                .then(ClientCommandManager.literal("enable")
                                    .executes(context -> setPlayerListMonitoring(context, true)))
                                .then(ClientCommandManager.literal("disable")
                                    .executes(context -> setPlayerListMonitoring(context, false))))
                            .then(ClientCommandManager.literal("nearby")
                                .then(ClientCommandManager.literal("enable")
                                    .executes(context -> setNearbyPlayerDetection(context, true)))
                                .then(ClientCommandManager.literal("disable")
                                    .executes(context -> setNearbyPlayerDetection(context, false))))
                            .then(ClientCommandManager.literal("radius")
                                .then(ClientCommandManager.argument("blocks", DoubleArgumentType.doubleArg(1.0, 128.0))
                                    .executes(context -> setNearbyPlayerDetectionRadius(context)))))
                        
                        // /pokealert antiafk avoid <add/remove/list>
                        .then(ClientCommandManager.literal("avoid")
                            .executes(context -> listPlayersToAvoid(context))
                            .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("player", StringArgumentType.greedyString())
                                    .executes(context -> addPlayerToAvoid(context))))
                            .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("player", StringArgumentType.greedyString())
                                    .executes(context -> removePlayerToAvoid(context))))
                            .then(ClientCommandManager.literal("list")
                                .executes(context -> listPlayersToAvoid(context))))
                        
                        // /pokealert antiafk queue <initial/replenish/locations/timeouts>
                        .then(ClientCommandManager.literal("queue")
                            .then(ClientCommandManager.literal("initial")
                                .then(ClientCommandManager.argument("size", IntegerArgumentType.integer(3, 10))
                                    .executes(context -> setInitialQueueSize(context))))
                            .then(ClientCommandManager.literal("replenish")
                                .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1, 5))
                                    .executes(context -> setReplenishCount(context))))
                            .then(ClientCommandManager.literal("locations")
                                .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1, 10))
                                    .executes(context -> setLocationsForStep5(context))))
                            .then(ClientCommandManager.literal("timeouts")
                                .then(ClientCommandManager.argument("max", IntegerArgumentType.integer(1, 10))
                                    .executes(context -> setMaxConsecutiveTimeouts(context))))))
                    
                    // ========== Phase 3: Human-like Behavior Commands ==========
                    
                    // /pokealert behavior <enable/disable/status/reset>
                    .then(ClientCommandManager.literal("behavior")
                        .executes(context -> showBehaviorStatus(context))
                        .then(ClientCommandManager.literal("enable")
                            .executes(context -> setHumanLikeBehavior(context, true)))
                        .then(ClientCommandManager.literal("disable")
                            .executes(context -> setHumanLikeBehavior(context, false)))
                        .then(ClientCommandManager.literal("status")
                            .executes(context -> showBehaviorStatus(context)))
                        .then(ClientCommandManager.literal("reset")
                            .executes(context -> resetBehaviorSettings(context)))
                        .then(ClientCommandManager.literal("pause")
                            .then(ClientCommandManager.literal("long")
                                .then(ClientCommandManager.argument("minMs", IntegerArgumentType.integer(1000, 30000))
                                    .then(ClientCommandManager.argument("maxMs", IntegerArgumentType.integer(1000, 60000))
                                        .executes(context -> setLongPauseRange(context)))))
                            .then(ClientCommandManager.literal("break")
                                .then(ClientCommandManager.argument("minMs", IntegerArgumentType.integer(10000, 120000))
                                    .then(ClientCommandManager.argument("maxMs", IntegerArgumentType.integer(10000, 300000))
                                        .executes(context -> setBreakPauseRange(context))))))
                        .then(ClientCommandManager.literal("chance")
                            .then(ClientCommandManager.literal("longpause")
                                .then(ClientCommandManager.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                                    .executes(context -> setLongPauseChance(context))))
                            .then(ClientCommandManager.literal("breakpause")
                                .then(ClientCommandManager.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                                    .executes(context -> setBreakPauseChance(context))))
                            .then(ClientCommandManager.literal("backtrack")
                                .then(ClientCommandManager.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                                    .executes(context -> setBacktrackChance(context))))
                            .then(ClientCommandManager.literal("walk")
                                .then(ClientCommandManager.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                                    .executes(context -> setWalkChance(context))))
                            .then(ClientCommandManager.literal("hotbar")
                                .then(ClientCommandManager.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                                    .executes(context -> setHotbarSwitchChance(context))))
                            .then(ClientCommandManager.literal("jump")
                                .then(ClientCommandManager.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                                    .executes(context -> setJumpWhileMovingChance(context))))
                            .then(ClientCommandManager.literal("lookaround")
                                .then(ClientCommandManager.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                                    .executes(context -> setLookAroundChance(context))))))
            );
        });
    }
    
    private static int showHelp(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        
        // Version header
        source.sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] ").formatted(Formatting.GRAY))
                .append(Text.literal("v3.0.0").formatted(Formatting.GOLD))
                .append(Text.literal(" - Pokémon Detection Mod").formatted(Formatting.WHITE))
        );
        
        // Keybind info
        source.sendFeedback(
            Text.literal("  ").formatted(Formatting.GRAY)
                .append(Text.literal("⚡").formatted(Formatting.YELLOW))
                .append(Text.literal(" Quick Toggle: Press ").formatted(Formatting.WHITE))
                .append(Text.literal("[").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(":").formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal("]").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(" to enable/disable").formatted(Formatting.GRAY))
                .append(Text.literal(" (customizable)").formatted(Formatting.DARK_GRAY))
        );
        source.sendFeedback(
            Text.literal("  ").formatted(Formatting.GRAY)
                .append(Text.literal("⏰").formatted(Formatting.YELLOW))
                .append(Text.literal(" Egg Timer: Press ").formatted(Formatting.WHITE))
                .append(Text.literal("[").formatted(Formatting.DARK_GRAY))
                .append(Text.literal("'").formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal("]").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(" to start egg timer").formatted(Formatting.GRAY))
                .append(Text.literal(" (customizable)").formatted(Formatting.DARK_GRAY))
        );
        
        source.sendFeedback(Text.empty()); // Empty line
        
        // Basic Commands section
        source.sendFeedback(Text.literal("━━━ ").formatted(Formatting.DARK_GRAY)
            .append(Text.literal("Basic Commands").formatted(Formatting.AQUA))
            .append(Text.literal(" ━━━").formatted(Formatting.DARK_GRAY)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("help").formatted(Formatting.GREEN))
            .append(Text.literal(" - ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("Show this help menu").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("enable").formatted(Formatting.GREEN))
            .append(Text.literal(" - ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("Enable the mod").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("disable").formatted(Formatting.RED))
            .append(Text.literal(" - ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("Disable the mod").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("status").formatted(Formatting.AQUA))
            .append(Text.literal(" - ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("Show current configuration").formatted(Formatting.WHITE)));
        
        source.sendFeedback(Text.empty()); // Empty line
        
        // Category Management section
        source.sendFeedback(Text.literal("━━━ ").formatted(Formatting.DARK_GRAY)
            .append(Text.literal("Detection Categories").formatted(Formatting.AQUA))
            .append(Text.literal(" ━━━").formatted(Formatting.DARK_GRAY)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("categories ").formatted(Formatting.GREEN))
            .append(Text.literal("<type> ").formatted(Formatting.LIGHT_PURPLE))
            .append(Text.literal("<enable|disable>").formatted(Formatting.LIGHT_PURPLE)));
        source.sendFeedback(Text.literal("    ").formatted(Formatting.GRAY)
            .append(Text.literal("Types: ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("legendaries").formatted(Formatting.GOLD))
            .append(Text.literal(", ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("mythics").formatted(Formatting.DARK_PURPLE))
            .append(Text.literal(", ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("shinies").formatted(Formatting.LIGHT_PURPLE))
            .append(Text.literal(", ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("starters").formatted(Formatting.GREEN)));
        source.sendFeedback(Text.literal("          ").formatted(Formatting.GRAY)
            .append(Text.literal("babies").formatted(Formatting.AQUA))
            .append(Text.literal(", ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("ultrabeasts").formatted(Formatting.DARK_AQUA))
            .append(Text.literal(", ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("paradox").formatted(Formatting.DARK_RED)));
        
        source.sendFeedback(Text.empty()); // Empty line
        
        // List Management section
        source.sendFeedback(Text.literal("━━━ ").formatted(Formatting.DARK_GRAY)
            .append(Text.literal("List Management").formatted(Formatting.AQUA))
            .append(Text.literal(" ━━━").formatted(Formatting.DARK_GRAY)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("whitelist ").formatted(Formatting.GREEN))
            .append(Text.literal("<add|remove|list> ").formatted(Formatting.LIGHT_PURPLE))
            .append(Text.literal("[pokemon]").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("    ➤ ").formatted(Formatting.DARK_GREEN)
            .append(Text.literal("Manage Pokémon to track").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("blacklist ").formatted(Formatting.RED))
            .append(Text.literal("<add|remove|list> ").formatted(Formatting.LIGHT_PURPLE))
            .append(Text.literal("[pokemon]").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("    ➤ ").formatted(Formatting.DARK_RED)
            .append(Text.literal("Manage Pokémon to exclude").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("excludedworlds ").formatted(Formatting.DARK_AQUA))
            .append(Text.literal("<add|remove|list> ").formatted(Formatting.LIGHT_PURPLE))
            .append(Text.literal("[world]").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("    ➤ ").formatted(Formatting.DARK_AQUA)
            .append(Text.literal("Manage worlds to ignore").formatted(Formatting.WHITE)));
        
        source.sendFeedback(Text.empty()); // Empty line
        
        // Egg Timer section
        source.sendFeedback(Text.literal("━━━ ").formatted(Formatting.DARK_GRAY)
            .append(Text.literal("Egg Timer").formatted(Formatting.AQUA))
            .append(Text.literal(" ━━━").formatted(Formatting.DARK_GRAY)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("timer").formatted(Formatting.GOLD)));
        source.sendFeedback(Text.literal("    ➤ ").formatted(Formatting.DARK_GRAY)
            .append(Text.literal("Toggle egg timer (keybind alternative)").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("eggtimer start ").formatted(Formatting.GREEN))
            .append(Text.literal("[minutes]").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("    ➤ ").formatted(Formatting.DARK_GREEN)
            .append(Text.literal("Start egg timer (default 30 min)").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("eggtimer stop").formatted(Formatting.RED)));
        source.sendFeedback(Text.literal("    ➤ ").formatted(Formatting.DARK_RED)
            .append(Text.literal("Stop the current egg timer").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("eggtimer status").formatted(Formatting.AQUA)));
        source.sendFeedback(Text.literal("    ➤ ").formatted(Formatting.DARK_AQUA)
            .append(Text.literal("Check timer status").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("eggtimer duration ").formatted(Formatting.LIGHT_PURPLE))
            .append(Text.literal("<minutes>").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("    ➤ ").formatted(Formatting.DARK_PURPLE)
            .append(Text.literal("Set default duration").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("eggtimer notifications text ").formatted(Formatting.GREEN))
            .append(Text.literal("<enable|disable>").formatted(Formatting.LIGHT_PURPLE)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("eggtimer notifications telegram ").formatted(Formatting.GREEN))
            .append(Text.literal("<enable|disable>").formatted(Formatting.LIGHT_PURPLE)));
        
        source.sendFeedback(Text.empty()); // Empty line
        
        // View Lists section
        source.sendFeedback(Text.literal("━━━ ").formatted(Formatting.DARK_GRAY)
            .append(Text.literal("View Pokémon Lists").formatted(Formatting.AQUA))
            .append(Text.literal(" ━━━").formatted(Formatting.DARK_GRAY)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("list ").formatted(Formatting.GREEN))
            .append(Text.literal("<type>").formatted(Formatting.LIGHT_PURPLE)));
        source.sendFeedback(Text.literal("    ").formatted(Formatting.GRAY)
            .append(Text.literal("Types: ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("whitelist").formatted(Formatting.GREEN))
            .append(Text.literal(", ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("blacklist").formatted(Formatting.RED))
            .append(Text.literal(", ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("legendaries").formatted(Formatting.GOLD))
            .append(Text.literal(", ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("mythics").formatted(Formatting.DARK_PURPLE)));
        source.sendFeedback(Text.literal("          ").formatted(Formatting.GRAY)
            .append(Text.literal("shinies").formatted(Formatting.LIGHT_PURPLE))
            .append(Text.literal(", ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("starters").formatted(Formatting.GREEN))
            .append(Text.literal(", ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("babies").formatted(Formatting.AQUA))
            .append(Text.literal(", ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("ultrabeasts").formatted(Formatting.DARK_AQUA))
            .append(Text.literal(", ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("paradox").formatted(Formatting.DARK_RED)));
        
        source.sendFeedback(Text.empty()); // Empty line
        
        // Notification Settings section
        source.sendFeedback(Text.literal("━━━ ").formatted(Formatting.DARK_GRAY)
            .append(Text.literal("Notification Settings").formatted(Formatting.AQUA))
            .append(Text.literal(" ━━━").formatted(Formatting.DARK_GRAY)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("notifications ").formatted(Formatting.GREEN))
            .append(Text.literal("<type> ").formatted(Formatting.LIGHT_PURPLE))
            .append(Text.literal("<enable|disable>").formatted(Formatting.LIGHT_PURPLE)));
        source.sendFeedback(Text.literal("    ").formatted(Formatting.GRAY)
            .append(Text.literal("Types: ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("text").formatted(Formatting.WHITE))
            .append(Text.literal(", ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("sound").formatted(Formatting.BLUE))
            .append(Text.literal(", ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("telegram").formatted(Formatting.DARK_BLUE)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("soundvolume ").formatted(Formatting.GREEN))
            .append(Text.literal("<0-100>").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("    ➤ ").formatted(Formatting.DARK_GREEN)
            .append(Text.literal("Set in-game sound volume (0-100%)").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("blacklistchar ").formatted(Formatting.GREEN))
            .append(Text.literal("<character>").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("    ➤ ").formatted(Formatting.DARK_GREEN)
            .append(Text.literal("Set character to filter Pokémon names (multiplayer)").formatted(Formatting.WHITE)));
        
        source.sendFeedback(Text.empty()); // Empty line
        
        // Telegram Configuration section
        source.sendFeedback(Text.literal("━━━ ").formatted(Formatting.DARK_GRAY)
            .append(Text.literal("Telegram Configuration").formatted(Formatting.AQUA))
            .append(Text.literal(" ━━━").formatted(Formatting.DARK_GRAY)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("telegram status").formatted(Formatting.AQUA)));
        source.sendFeedback(Text.literal("    ➤ ").formatted(Formatting.DARK_AQUA)
            .append(Text.literal("Show Telegram configuration (masked)").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("telegram token ").formatted(Formatting.GREEN))
            .append(Text.literal("<token>").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("telegram chatid ").formatted(Formatting.GREEN))
            .append(Text.literal("<chatId>").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("telegram apiurl ").formatted(Formatting.GREEN))
            .append(Text.literal("<url>").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("telegram ratelimit ").formatted(Formatting.GREEN))
            .append(Text.literal("<maxPerMinute>").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("telegram cooldown ").formatted(Formatting.GREEN))
            .append(Text.literal("<seconds>").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("telegram test").formatted(Formatting.AQUA)));
        source.sendFeedback(Text.literal("    ➤ ").formatted(Formatting.DARK_AQUA)
            .append(Text.literal("Test Telegram connection").formatted(Formatting.WHITE)));
        
        source.sendFeedback(Text.empty()); // Empty line
        
        // DM Detection section
        source.sendFeedback(Text.literal("━━━ ").formatted(Formatting.DARK_GRAY)
            .append(Text.literal("DM Detection").formatted(Formatting.AQUA))
            .append(Text.literal(" ━━━").formatted(Formatting.DARK_GRAY)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("dm ").formatted(Formatting.GREEN))
            .append(Text.literal("<enable|disable|status>").formatted(Formatting.LIGHT_PURPLE)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("dm notifications telegram ").formatted(Formatting.GREEN))
            .append(Text.literal("<enable|disable>").formatted(Formatting.LIGHT_PURPLE)));
        source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
            .append(Text.literal("dm notifications ingame ").formatted(Formatting.GREEN))
            .append(Text.literal("<enable|disable>").formatted(Formatting.LIGHT_PURPLE)));
        
        source.sendFeedback(Text.empty()); // Empty line
        
        // Egg Hatcher features (only show if enabled)
        PokeAlertConfig config = PokeAlertClient.getInstance().config;
        if (config.eggHatcherEnabled) {
            source.sendFeedback(Text.literal("━━━ ").formatted(Formatting.DARK_GRAY)
                .append(Text.literal("Egg Hatcher").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal(" ━━━").formatted(Formatting.DARK_GRAY)));
            
            // Description
            source.sendFeedback(
                Text.literal("  ").formatted(Formatting.GRAY)
                    .append(Text.literal("Automatically returns from spawn to overworld realm").formatted(Formatting.WHITE))
            );
            source.sendFeedback(
                Text.literal("  ").formatted(Formatting.GRAY)
                    .append(Text.literal("Manages Anti-AFK during realm transitions").formatted(Formatting.WHITE))
            );
            source.sendFeedback(Text.empty()); // Empty line
            
            // Modes
            source.sendFeedback(Text.literal("  Modes:").formatted(Formatting.AQUA));
            source.sendFeedback(
                Text.literal("    • ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal("AUTO").formatted(Formatting.GREEN))
                    .append(Text.literal(" - Automatically triggers on spawn detection").formatted(Formatting.WHITE))
            );
            source.sendFeedback(
                Text.literal("    • ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal("DISABLED").formatted(Formatting.RED))
                    .append(Text.literal(" - Completely disabled").formatted(Formatting.WHITE))
            );
            source.sendFeedback(Text.empty()); // Empty line
            
            // Commands
            source.sendFeedback(Text.literal("  Commands:").formatted(Formatting.AQUA));
            source.sendFeedback(
                Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
                    .append(Text.literal("realm").formatted(Formatting.GREEN))
                    .append(Text.literal(" - ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal("Check egg hatcher automation status").formatted(Formatting.WHITE))
            );
            source.sendFeedback(
                Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
                    .append(Text.literal("realm toggle").formatted(Formatting.GREEN))
                    .append(Text.literal(" - ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal("Enable/disable automation").formatted(Formatting.WHITE))
            );
            source.sendFeedback(
                Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
                    .append(Text.literal("realm status").formatted(Formatting.GREEN))
                    .append(Text.literal(" - ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal("Check current status").formatted(Formatting.WHITE))
            );
            source.sendFeedback(
                Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
                    .append(Text.literal("realm command ").formatted(Formatting.GREEN))
                    .append(Text.literal("<command>").formatted(Formatting.GRAY))
                    .append(Text.literal(" - ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal("Set realm return command").formatted(Formatting.WHITE))
            );
            source.sendFeedback(Text.empty()); // Empty line
            
            // Anti-AFK section
            source.sendFeedback(Text.literal("━━━ ").formatted(Formatting.DARK_GRAY)
                .append(Text.literal("Anti-AFK Settings").formatted(Formatting.AQUA))
                .append(Text.literal(" ━━━").formatted(Formatting.DARK_GRAY)));
            source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
                .append(Text.literal("antiafk region ").formatted(Formatting.GREEN))
                .append(Text.literal("<get|set|reset>").formatted(Formatting.LIGHT_PURPLE)));
            source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
                .append(Text.literal("antiafk region set ").formatted(Formatting.GREEN))
                .append(Text.literal("<x1> <z1> <x2> <z2>").formatted(Formatting.GRAY)));
            source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
                .append(Text.literal("antiafk threshold arrival ").formatted(Formatting.GREEN))
                .append(Text.literal("<blocks>").formatted(Formatting.GRAY)));
            source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
                .append(Text.literal("antiafk threshold teleport ").formatted(Formatting.GREEN))
                .append(Text.literal("<blocks>").formatted(Formatting.GRAY)));
            source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
                .append(Text.literal("antiafk timing ").formatted(Formatting.GREEN))
                .append(Text.literal("<coordinate|realmspawn|realmoverworld|playermonitor|timeout|reset>").formatted(Formatting.LIGHT_PURPLE)));
            source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
                .append(Text.literal("antiafk safety monitoring ").formatted(Formatting.GREEN))
                .append(Text.literal("<enable|disable>").formatted(Formatting.LIGHT_PURPLE)));
            source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
                .append(Text.literal("antiafk safety nearby ").formatted(Formatting.GREEN))
                .append(Text.literal("<enable|disable>").formatted(Formatting.LIGHT_PURPLE)));
            source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
                .append(Text.literal("antiafk safety radius ").formatted(Formatting.GREEN))
                .append(Text.literal("<blocks>").formatted(Formatting.GRAY)));
            source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
                .append(Text.literal("antiafk avoid ").formatted(Formatting.GREEN))
                .append(Text.literal("<add|remove|list> ").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal("[player]").formatted(Formatting.GRAY)));
            source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
                .append(Text.literal("antiafk queue ").formatted(Formatting.GREEN))
                .append(Text.literal("<initial|replenish|locations|timeouts> ").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal("<value>").formatted(Formatting.GRAY)));
            
            source.sendFeedback(Text.empty()); // Empty line
            
            // Human-like Behavior section
            source.sendFeedback(Text.literal("━━━ ").formatted(Formatting.DARK_GRAY)
                .append(Text.literal("Human-like Behavior").formatted(Formatting.AQUA))
                .append(Text.literal(" ━━━").formatted(Formatting.DARK_GRAY)));
            source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
                .append(Text.literal("behavior ").formatted(Formatting.GREEN))
                .append(Text.literal("<enable|disable|status|reset>").formatted(Formatting.LIGHT_PURPLE)));
            source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
                .append(Text.literal("behavior pause long ").formatted(Formatting.GREEN))
                .append(Text.literal("<minMs> <maxMs>").formatted(Formatting.GRAY)));
            source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
                .append(Text.literal("behavior pause break ").formatted(Formatting.GREEN))
                .append(Text.literal("<minMs> <maxMs>").formatted(Formatting.GRAY)));
            source.sendFeedback(Text.literal("  /pokealert ").formatted(Formatting.YELLOW)
                .append(Text.literal("behavior chance ").formatted(Formatting.GREEN))
                .append(Text.literal("<longpause|breakpause|backtrack|walk|hotbar|jump|lookaround> ").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal("<percent>").formatted(Formatting.GRAY)));
            
            source.sendFeedback(Text.empty()); // Empty line
            
            // Keybind
            source.sendFeedback(
                Text.literal("  ").formatted(Formatting.GRAY)
                    .append(Text.literal("🏠").formatted(Formatting.AQUA))
                    .append(Text.literal(" Press ").formatted(Formatting.WHITE))
                    .append(Text.literal("[").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal("Home").formatted(Formatting.GOLD, Formatting.BOLD))
                    .append(Text.literal("]").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(" to enable/disable automation").formatted(Formatting.GRAY))
            );
            source.sendFeedback(Text.empty()); // Empty line
        }
        
        // Footer tip
        source.sendFeedback(Text.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").formatted(Formatting.DARK_GRAY));
        source.sendFeedback(
            Text.literal("  💡 ").formatted(Formatting.YELLOW)
                .append(Text.literal("Tip: ").formatted(Formatting.AQUA))
                .append(Text.literal("Use Mod Menu for visual configuration!").formatted(Formatting.WHITE))
        );
        
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
        } else {
            // Show all Pokemon regardless of count
            for (String pokemon : pokemonList) {
                source.sendFeedback(Text.literal("  • ").formatted(Formatting.GRAY)
                    .append(Text.literal(formatPokemonName(pokemon)).formatted(Formatting.WHITE)));
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
    
    private static String getPredefinedCategory(String pokemon) {
        // Check each predefined category
        for (String p : PokemonLists.legendaries) {
            if (p.equalsIgnoreCase(pokemon)) {
                return "Legendaries";
            }
        }
        for (String p : PokemonLists.mythics) {
            if (p.equalsIgnoreCase(pokemon)) {
                return "Mythics";
            }
        }
        for (String p : PokemonLists.starter) {
            if (p.equalsIgnoreCase(pokemon)) {
                return "Starters";
            }
        }
        for (String p : PokemonLists.babies) {
            if (p.equalsIgnoreCase(pokemon)) {
                return "Babies";
            }
        }
        for (String p : PokemonLists.ultra_beasts) {
            if (p.equalsIgnoreCase(pokemon)) {
                return "Ultra Beasts";
            }
        }
        for (String p : PokemonLists.paradox_mons) {
            if (p.equalsIgnoreCase(pokemon)) {
                return "Paradox";
            }
        }
        return null;
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
        FabricClientCommandSource source = context.getSource();
        
        // Check if already in whitelist
        List<String> whitelist = new ArrayList<>(Arrays.asList(config.broadcastWhitelist));
        if (whitelist.contains(pokemon)) {
            source.sendError(Text.literal(pokemon + " is already in the whitelist"));
            return 1;
        }
        
        // Check if in blacklist
        List<String> blacklist = new ArrayList<>(Arrays.asList(config.broadcastBlacklist));
        if (blacklist.contains(pokemon)) {
            // Prompt user about conflict
            source.sendFeedback(
                Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                    .append(Text.literal("Warning: ").formatted(Formatting.YELLOW))
                    .append(Text.literal(pokemon).formatted(Formatting.WHITE))
                    .append(Text.literal(" is currently in the blacklist").formatted(Formatting.GRAY))
            );
            source.sendFeedback(
                Text.literal("  ").formatted(Formatting.GRAY)
                    .append(Text.literal("→ Use ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal("/pokealert blacklist remove " + pokemon).formatted(Formatting.AQUA))
                    .append(Text.literal(" first").formatted(Formatting.DARK_GRAY))
            );
            source.sendFeedback(
                Text.literal("  ").formatted(Formatting.GRAY)
                    .append(Text.literal("→ Then ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal("/pokealert whitelist add " + pokemon).formatted(Formatting.AQUA))
            );
            return 1;
        }
        
        // Check if in predefined lists and require confirmation
        String predefinedCategory = getPredefinedCategory(pokemon);
        if (predefinedCategory != null) {
            long currentTime = System.currentTimeMillis();
            
            // Check if this is a confirmation (second command within window)
            if (lastRedundantWhitelistPokemon != null && 
                lastRedundantWhitelistPokemon.equalsIgnoreCase(pokemon) && 
                currentTime - lastRedundantWhitelistTime <= CONFIRMATION_WINDOW) {
                
                // User confirmed, proceed with addition
                lastRedundantWhitelistPokemon = null;
                lastRedundantWhitelistTime = 0;
                
                source.sendFeedback(
                    Text.literal("[").formatted(Formatting.GRAY)
                        .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                        .append(Text.literal("] ").formatted(Formatting.GRAY))
                        .append(Text.literal("Confirmed: Adding ").formatted(Formatting.YELLOW))
                        .append(Text.literal(pokemon).formatted(Formatting.WHITE))
                        .append(Text.literal(" to whitelist despite being in ").formatted(Formatting.GRAY))
                        .append(Text.literal(predefinedCategory).formatted(Formatting.GOLD))
                );
                // Continue with addition below
            } else {
                // First attempt - show warning and require confirmation
                lastRedundantWhitelistPokemon = pokemon;
                lastRedundantWhitelistTime = currentTime;
                
                source.sendFeedback(
                    Text.literal("[").formatted(Formatting.GRAY)
                        .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                        .append(Text.literal("] ").formatted(Formatting.GRAY))
                        .append(Text.literal("Warning: ").formatted(Formatting.YELLOW))
                        .append(Text.literal(pokemon).formatted(Formatting.WHITE))
                        .append(Text.literal(" is already in ").formatted(Formatting.GRAY))
                        .append(Text.literal(predefinedCategory).formatted(Formatting.GOLD))
                );
                source.sendFeedback(
                    Text.literal("  ").formatted(Formatting.GRAY)
                        .append(Text.literal("→ ").formatted(Formatting.DARK_GRAY))
                        .append(Text.literal("This may be redundant if the category is enabled").formatted(Formatting.GRAY))
                );
                source.sendFeedback(
                    Text.literal("  ").formatted(Formatting.GRAY)
                        .append(Text.literal("→ ").formatted(Formatting.DARK_GRAY))
                        .append(Text.literal("Run the command again within 10 seconds to confirm").formatted(Formatting.AQUA))
                );
                return 1; // Exit without adding
            }
        }
        
        // Add to whitelist
        whitelist.add(pokemon);
        config.broadcastWhitelist = whitelist.toArray(new String[0]);
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        source.sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Added ").formatted(Formatting.GRAY))
                .append(Text.literal(pokemon).formatted(Formatting.WHITE))
                .append(Text.literal(" to whitelist").formatted(Formatting.GREEN))
        );
        
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
        FabricClientCommandSource source = context.getSource();
        
        // Check if already in blacklist
        List<String> blacklist = new ArrayList<>(Arrays.asList(config.broadcastBlacklist));
        if (blacklist.contains(pokemon)) {
            source.sendError(Text.literal(pokemon + " is already in the blacklist"));
            return 1;
        }
        
        // Check if in whitelist
        List<String> whitelist = new ArrayList<>(Arrays.asList(config.broadcastWhitelist));
        if (whitelist.contains(pokemon)) {
            // Prompt user about conflict
            source.sendFeedback(
                Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                    .append(Text.literal("Warning: ").formatted(Formatting.YELLOW))
                    .append(Text.literal(pokemon).formatted(Formatting.WHITE))
                    .append(Text.literal(" is currently in the whitelist").formatted(Formatting.GRAY))
            );
            source.sendFeedback(
                Text.literal("  ").formatted(Formatting.GRAY)
                    .append(Text.literal("→ Use ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal("/pokealert whitelist remove " + pokemon).formatted(Formatting.AQUA))
                    .append(Text.literal(" first").formatted(Formatting.DARK_GRAY))
            );
            source.sendFeedback(
                Text.literal("  ").formatted(Formatting.GRAY)
                    .append(Text.literal("→ Then ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal("/pokealert blacklist add " + pokemon).formatted(Formatting.AQUA))
            );
            return 1;
        }
        
        // Check if in predefined lists and require confirmation
        String predefinedCategory = getPredefinedCategory(pokemon);
        if (predefinedCategory != null) {
            long currentTime = System.currentTimeMillis();
            
            // Check if this is a confirmation (second command within window)
            if (lastRedundantBlacklistPokemon != null && 
                lastRedundantBlacklistPokemon.equalsIgnoreCase(pokemon) && 
                currentTime - lastRedundantBlacklistTime <= CONFIRMATION_WINDOW) {
                
                // User confirmed, proceed with addition
                lastRedundantBlacklistPokemon = null;
                lastRedundantBlacklistTime = 0;
                
                source.sendFeedback(
                    Text.literal("[").formatted(Formatting.GRAY)
                        .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                        .append(Text.literal("] ").formatted(Formatting.GRAY))
                        .append(Text.literal("Confirmed: Adding ").formatted(Formatting.YELLOW))
                        .append(Text.literal(pokemon).formatted(Formatting.WHITE))
                        .append(Text.literal(" to blacklist despite being in ").formatted(Formatting.GRAY))
                        .append(Text.literal(predefinedCategory).formatted(Formatting.GOLD))
                );
                source.sendFeedback(
                    Text.literal("  ").formatted(Formatting.GRAY)
                        .append(Text.literal("→ ").formatted(Formatting.DARK_GRAY))
                        .append(Text.literal("Consider disabling the ").formatted(Formatting.GRAY))
                        .append(Text.literal(predefinedCategory).formatted(Formatting.GOLD))
                        .append(Text.literal(" category instead").formatted(Formatting.GRAY))
                );
                // Continue with addition below
            } else {
                // First attempt - show warning and require confirmation
                lastRedundantBlacklistPokemon = pokemon;
                lastRedundantBlacklistTime = currentTime;
                
                source.sendFeedback(
                    Text.literal("[").formatted(Formatting.GRAY)
                        .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                        .append(Text.literal("] ").formatted(Formatting.GRAY))
                        .append(Text.literal("Warning: ").formatted(Formatting.YELLOW))
                        .append(Text.literal(pokemon).formatted(Formatting.WHITE))
                        .append(Text.literal(" is in ").formatted(Formatting.GRAY))
                        .append(Text.literal(predefinedCategory).formatted(Formatting.GOLD))
                );
                source.sendFeedback(
                    Text.literal("  ").formatted(Formatting.GRAY)
                        .append(Text.literal("→ ").formatted(Formatting.DARK_GRAY))
                        .append(Text.literal("To exclude, you can disable the entire category instead").formatted(Formatting.GRAY))
                );
                source.sendFeedback(
                    Text.literal("  ").formatted(Formatting.GRAY)
                        .append(Text.literal("→ ").formatted(Formatting.DARK_GRAY))
                        .append(Text.literal("Run the command again within 10 seconds to confirm blacklisting").formatted(Formatting.AQUA))
                );
                return 1; // Exit without adding
            }
        }
        
        // Add to blacklist
        blacklist.add(pokemon);
        config.broadcastBlacklist = blacklist.toArray(new String[0]);
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        source.sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Added ").formatted(Formatting.GRAY))
                .append(Text.literal(pokemon).formatted(Formatting.WHITE))
                .append(Text.literal(" to blacklist").formatted(Formatting.RED))
        );
        
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
    
    private static int startEggTimer(CommandContext<FabricClientCommandSource> context) {
        EggTimerManager timerManager = EggTimerManager.getInstance();
        FabricClientCommandSource source = context.getSource();
        
        if (timerManager.isTimerRunning()) {
            int remaining = timerManager.getRemainingMinutes();
            source.sendFeedback(
                Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                    .append(Text.literal("⏰ ").formatted(Formatting.YELLOW))
                    .append(Text.literal("Egg timer already running: ").formatted(Formatting.WHITE))
                    .append(Text.literal(remaining + " minutes remaining").formatted(Formatting.AQUA))
            );
        } else {
            timerManager.startTimer();
        }
        
        return 1;
    }
    
    private static int startEggTimer(CommandContext<FabricClientCommandSource> context, int minutes) {
        EggTimerManager timerManager = EggTimerManager.getInstance();
        FabricClientCommandSource source = context.getSource();
        
        if (timerManager.isTimerRunning()) {
            int remaining = timerManager.getRemainingMinutes();
            source.sendFeedback(
                Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                    .append(Text.literal("⏰ ").formatted(Formatting.YELLOW))
                    .append(Text.literal("Egg timer already running: ").formatted(Formatting.WHITE))
                    .append(Text.literal(remaining + " minutes remaining").formatted(Formatting.AQUA))
            );
        } else {
            timerManager.startTimer(minutes);
        }
        
        return 1;
    }
    
    private static int stopEggTimer(CommandContext<FabricClientCommandSource> context) {
        EggTimerManager timerManager = EggTimerManager.getInstance();
        FabricClientCommandSource source = context.getSource();
        
        if (timerManager.stopTimer()) {
            source.sendFeedback(
                Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                    .append(Text.literal("⏰ ").formatted(Formatting.YELLOW))
                    .append(Text.literal("Egg timer stopped").formatted(Formatting.RED))
            );
        } else {
            source.sendFeedback(
                Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                    .append(Text.literal("⏰ ").formatted(Formatting.YELLOW))
                    .append(Text.literal("No egg timer is running").formatted(Formatting.GRAY))
            );
        }
        
        return 1;
    }
    
    private static int getEggTimerStatus(CommandContext<FabricClientCommandSource> context) {
        EggTimerManager timerManager = EggTimerManager.getInstance();
        FabricClientCommandSource source = context.getSource();
        
        if (timerManager.isTimerRunning()) {
            int remaining = timerManager.getRemainingMinutes();
            source.sendFeedback(
                Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                    .append(Text.literal("⏰ ").formatted(Formatting.YELLOW))
                    .append(Text.literal("Egg timer: ").formatted(Formatting.WHITE))
                    .append(Text.literal(remaining + " minutes remaining").formatted(Formatting.AQUA))
            );
        } else {
            source.sendFeedback(
                Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                    .append(Text.literal("⏰ ").formatted(Formatting.YELLOW))
                    .append(Text.literal("No egg timer is running").formatted(Formatting.GRAY))
            );
        }
        
        return 1;
    }
    
    private static int setEggTimerDuration(CommandContext<FabricClientCommandSource> context) {
        int minutes = IntegerArgumentType.getInteger(context, "minutes");
        PokeAlertConfig config = ConfigManager.getConfig();
        FabricClientCommandSource source = context.getSource();
        
        config.eggTimerDuration = minutes;
        ConfigManager.updateConfig(config);
        
        source.sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] ").formatted(Formatting.GRAY))
                .append(Text.literal("⏰ ").formatted(Formatting.YELLOW))
                .append(Text.literal("Default egg timer duration set to ").formatted(Formatting.WHITE))
                .append(Text.literal(minutes + " minutes").formatted(Formatting.AQUA))
        );
        
        return 1;
    }
    
    private static int checkRealmStatus(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        EggHatcher manager = EggHatcher.getInstance();
        
        source.sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                .append(Text.literal("] ").formatted(Formatting.GRAY))
                .append(Text.literal("Egg Hatcher Status: ").formatted(Formatting.WHITE))
                .append(Text.literal(manager.getStatus()).formatted(
                    manager.getStatus().equals("Disabled") ? Formatting.RED :
                    manager.getStatus().startsWith("Running") ? Formatting.YELLOW :
                    Formatting.GREEN
                ))
        );
        
        if (manager.isAtSpawn() && source.getClient().player != null) {
            source.sendFeedback(
                Text.literal("  ")
                    .append(Text.literal("⚠ ").formatted(Formatting.YELLOW))
                    .append(Text.literal("Currently at spawn world").formatted(Formatting.YELLOW))
            );
        }
        
        return 1;
    }
    
    private static int toggleEggHatcher(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        PokeAlertConfig config = PokeAlertClient.getInstance().config;
        
        // Check if mod is enabled
        if (!config.modEnabled) {
            source.sendFeedback(
                Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                    .append(Text.literal("Egg Hatcher disabled - Enable PokeAlert first").formatted(Formatting.YELLOW))
            );
            return 0;
        }
        
        EggHatcher manager = EggHatcher.getInstance();
        manager.toggleAutomation();
        
        String status = manager.getStatus();
        source.sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                .append(Text.literal("] ").formatted(Formatting.GRAY))
                .append(Text.literal("Egg Hatcher: ").formatted(Formatting.WHITE))
                .append(Text.literal(status).formatted(
                    status.equals("Disabled") ? Formatting.RED : Formatting.GREEN
                ))
        );
        
        return 1;
    }
    
    private static int toggleEggTimer(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        PokeAlertConfig config = PokeAlertClient.getInstance().config;
        
        // Check if mod is enabled
        if (!config.modEnabled) {
            source.sendFeedback(
                Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                    .append(Text.literal("Egg Timer disabled - Enable PokeAlert first").formatted(Formatting.YELLOW))
            );
            return 0;
        }
        
        EggTimerManager timerManager = EggTimerManager.getInstance();
        timerManager.handleTimerToggle();
        
        return 1;
    }
    
    // ========== Phase 1: High Priority Missing Commands ==========
    
    private static int setBlacklistCharacter(CommandContext<FabricClientCommandSource> context) {
        String character = StringArgumentType.getString(context, "character");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        if (character.length() != 1) {
            context.getSource().sendError(Text.literal("Blacklist character must be a single character"));
            return 0;
        }
        
        config.blacklistCharacter = character;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Blacklist character set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(character).formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setSoundVolume(CommandContext<FabricClientCommandSource> context) {
        int volumePercent = IntegerArgumentType.getInteger(context, "volume");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.inGameSoundVolume = volumePercent / 100.0f;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Sound volume set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(volumePercent + "%").formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setEggTimerNotification(CommandContext<FabricClientCommandSource> context, String type, boolean enabled) {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        switch (type) {
            case "text" -> config.eggTimerTextNotification = enabled;
            case "telegram" -> config.eggTimerTelegramNotification = enabled;
        }
        
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Egg timer ").formatted(Formatting.GRAY))
                .append(Text.literal(type).formatted(Formatting.WHITE))
                .append(Text.literal(" notifications ").formatted(Formatting.GRAY))
                .append(Text.literal(enabled ? "ENABLED" : "DISABLED")
                    .formatted(enabled ? Formatting.GREEN : Formatting.RED))
        );
        
        return 1;
    }
    
    private static int setRealmReturnCommand(CommandContext<FabricClientCommandSource> context) {
        String command = StringArgumentType.getString(context, "command");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        if (!command.startsWith("/")) {
            context.getSource().sendError(Text.literal("Command must start with '/'"));
            return 0;
        }
        
        config.realmReturnCommand = command;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Realm return command set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(command).formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setDmDetection(CommandContext<FabricClientCommandSource> context, boolean enabled) {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.dmDetectionEnabled = enabled;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] DM Detection ").formatted(Formatting.GRAY))
                .append(Text.literal(enabled ? "ENABLED" : "DISABLED")
                    .formatted(enabled ? Formatting.GREEN : Formatting.RED))
        );
        
        return 1;
    }
    
    private static int showDmStatus(CommandContext<FabricClientCommandSource> context) {
        PokeAlertConfig config = ConfigManager.getConfig();
        FabricClientCommandSource source = context.getSource();
        
        source.sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] DM Detection Status").formatted(Formatting.WHITE))
        );
        
        source.sendFeedback(formatCategoryStatus("DM Detection", config.dmDetectionEnabled));
        source.sendFeedback(formatCategoryStatus("Telegram Notifications", config.dmTelegramNotification));
        source.sendFeedback(formatCategoryStatus("In-Game Notifications", config.dmInGameNotification));
        
        return 1;
    }
    
    private static int setDmNotification(CommandContext<FabricClientCommandSource> context, String type, boolean enabled) {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        switch (type) {
            case "telegram" -> config.dmTelegramNotification = enabled;
            case "ingame" -> config.dmInGameNotification = enabled;
        }
        
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] DM ").formatted(Formatting.GRAY))
                .append(Text.literal(type.equals("ingame") ? "in-game" : type).formatted(Formatting.WHITE))
                .append(Text.literal(" notifications ").formatted(Formatting.GRAY))
                .append(Text.literal(enabled ? "ENABLED" : "DISABLED")
                    .formatted(enabled ? Formatting.GREEN : Formatting.RED))
        );
        
        return 1;
    }
    
    private static int showTelegramStatus(CommandContext<FabricClientCommandSource> context) {
        PokeAlertConfig config = ConfigManager.getConfig();
        FabricClientCommandSource source = context.getSource();
        
        source.sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Telegram Configuration").formatted(Formatting.WHITE))
        );
        
        source.sendFeedback(formatCategoryStatus("Telegram Enabled", config.telegramEnabled));
        source.sendFeedback(Text.literal("  Bot Token: ").formatted(Formatting.GRAY)
            .append(Text.literal(config.telegramBotToken != null && !config.telegramBotToken.isEmpty() 
                ? "***" + config.telegramBotToken.substring(Math.max(0, config.telegramBotToken.length() - 4))
                : "Not set").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  Chat ID: ").formatted(Formatting.GRAY)
            .append(Text.literal(config.telegramChatId != null && !config.telegramChatId.isEmpty() 
                ? "***" + config.telegramChatId.substring(Math.max(0, config.telegramChatId.length() - 4))
                : "Not set").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  API URL: ").formatted(Formatting.GRAY)
            .append(Text.literal(config.telegramApiUrl).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  Rate Limit: ").formatted(Formatting.GRAY)
            .append(Text.literal(config.telegramMaxNotificationsPerMinute + " per minute").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  Cooldown: ").formatted(Formatting.GRAY)
            .append(Text.literal(config.telegramCooldownSeconds + " seconds").formatted(Formatting.WHITE)));
        source.sendFeedback(formatCategoryStatus("Valid Configuration", config.isTelegramValid()));
        
        return 1;
    }
    
    private static int setTelegramToken(CommandContext<FabricClientCommandSource> context) {
        String token = StringArgumentType.getString(context, "token");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.telegramBotToken = token;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Telegram bot token set").formatted(Formatting.GREEN))
        );
        
        return 1;
    }
    
    private static int setTelegramChatId(CommandContext<FabricClientCommandSource> context) {
        String chatId = StringArgumentType.getString(context, "chatId");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.telegramChatId = chatId;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Telegram chat ID set").formatted(Formatting.GREEN))
        );
        
        return 1;
    }
    
    private static int setTelegramApiUrl(CommandContext<FabricClientCommandSource> context) {
        String url = StringArgumentType.getString(context, "url");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            context.getSource().sendError(Text.literal("URL must start with http:// or https://"));
            return 0;
        }
        
        config.telegramApiUrl = url;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Telegram API URL set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(url).formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setTelegramRateLimit(CommandContext<FabricClientCommandSource> context) {
        int maxPerMinute = IntegerArgumentType.getInteger(context, "maxPerMinute");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.telegramMaxNotificationsPerMinute = maxPerMinute;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Telegram rate limit set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(maxPerMinute + " per minute").formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setTelegramCooldown(CommandContext<FabricClientCommandSource> context) {
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.telegramCooldownSeconds = seconds;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Telegram cooldown set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(seconds + " seconds").formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int testTelegramConnection(CommandContext<FabricClientCommandSource> context) {
        PokeAlertConfig config = ConfigManager.getConfig();
        FabricClientCommandSource source = context.getSource();
        
        if (!config.isTelegramValid()) {
            source.sendError(Text.literal("Telegram is not properly configured. Set bot token and chat ID first."));
            return 0;
        }
        
        source.sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Testing Telegram connection...").formatted(Formatting.YELLOW))
        );
        
        // Note: Actual test would require sending a test message
        // For now, just validate configuration
        source.sendFeedback(
            Text.literal("  Configuration appears valid. Send a test notification to verify.").formatted(Formatting.GRAY)
        );
        
        return 1;
    }
    
    // ========== Phase 2: Anti-AFK Commands ==========
    
    private static int showAntiAfkRegion(CommandContext<FabricClientCommandSource> context) {
        PokeAlertConfig config = ConfigManager.getConfig();
        FabricClientCommandSource source = context.getSource();
        
        source.sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Anti-AFK Region").formatted(Formatting.WHITE))
        );
        
        source.sendFeedback(Text.literal("  Corner 1: (").formatted(Formatting.GRAY)
            .append(Text.literal(String.valueOf(config.antiAfkRegionX1)).formatted(Formatting.WHITE))
            .append(Text.literal(", ").formatted(Formatting.GRAY))
            .append(Text.literal(String.valueOf(config.antiAfkRegionZ1)).formatted(Formatting.WHITE))
            .append(Text.literal(")").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("  Corner 2: (").formatted(Formatting.GRAY)
            .append(Text.literal(String.valueOf(config.antiAfkRegionX2)).formatted(Formatting.WHITE))
            .append(Text.literal(", ").formatted(Formatting.GRAY))
            .append(Text.literal(String.valueOf(config.antiAfkRegionZ2)).formatted(Formatting.WHITE))
            .append(Text.literal(")").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("  Size: ").formatted(Formatting.GRAY)
            .append(Text.literal(config.getAntiAfkRegionWidth() + " x " + config.getAntiAfkRegionDepth()).formatted(Formatting.WHITE)));
        
        if (config.isAntiAfkRegionTooSmall()) {
            source.sendFeedback(Text.literal("  ⚠ Region may be too small (recommended: 20x20 minimum)").formatted(Formatting.YELLOW));
        }
        
        return 1;
    }
    
    private static int setAntiAfkRegion(CommandContext<FabricClientCommandSource> context) {
        int x1 = IntegerArgumentType.getInteger(context, "x1");
        int z1 = IntegerArgumentType.getInteger(context, "z1");
        int x2 = IntegerArgumentType.getInteger(context, "x2");
        int z2 = IntegerArgumentType.getInteger(context, "z2");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        if (x1 == x2 || z1 == z2) {
            context.getSource().sendError(Text.literal("Region must have non-zero area (x1 != x2 and z1 != z2)"));
            return 0;
        }
        
        config.antiAfkRegionX1 = x1;
        config.antiAfkRegionZ1 = z1;
        config.antiAfkRegionX2 = x2;
        config.antiAfkRegionZ2 = z2;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Anti-AFK region set: (").formatted(Formatting.GRAY))
                .append(Text.literal(x1 + ", " + z1).formatted(Formatting.WHITE))
                .append(Text.literal(") to (").formatted(Formatting.GRAY))
                .append(Text.literal(x2 + ", " + z2).formatted(Formatting.WHITE))
                .append(Text.literal(")").formatted(Formatting.GRAY))
        );
        
        return 1;
    }
    
    private static int resetAntiAfkRegion(CommandContext<FabricClientCommandSource> context) {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.antiAfkRegionX1 = 0;
        config.antiAfkRegionZ1 = 0;
        config.antiAfkRegionX2 = 100;
        config.antiAfkRegionZ2 = 100;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Anti-AFK region reset to default (0,0 to 100,100)").formatted(Formatting.GREEN))
        );
        
        return 1;
    }
    
    private static int setArrivalThreshold(CommandContext<FabricClientCommandSource> context) {
        int blocks = IntegerArgumentType.getInteger(context, "blocks");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.arrivalThreshold = blocks;
        config.validateTimingConfig(); // Ensure teleport detection is valid
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Arrival threshold set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(blocks + " blocks").formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setTeleportDetectionOffset(CommandContext<FabricClientCommandSource> context) {
        int blocks = IntegerArgumentType.getInteger(context, "blocks");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.teleportDetectionOffset = blocks;
        config.validateTimingConfig(); // Ensure it's at least 2x arrival threshold
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Teleport detection offset set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(blocks + " blocks").formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int showTimingSettings(CommandContext<FabricClientCommandSource> context) {
        PokeAlertConfig config = ConfigManager.getConfig();
        FabricClientCommandSource source = context.getSource();
        
        source.sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Anti-AFK Timing Settings").formatted(Formatting.WHITE))
        );
        
        source.sendFeedback(Text.literal("  Coordinate Check: ").formatted(Formatting.GRAY)
            .append(Text.literal(config.coordinateCheckInterval + "ms").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  Realm Check (Spawn): ").formatted(Formatting.GRAY)
            .append(Text.literal(config.realmCheckIntervalSpawn + "ms").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  Realm Check (Overworld): ").formatted(Formatting.GRAY)
            .append(Text.literal(config.realmCheckIntervalOverworld + "ms").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  Player Monitor: ").formatted(Formatting.GRAY)
            .append(Text.literal(config.playerMonitorInterval + "ms").formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  Location Timeout: ").formatted(Formatting.GRAY)
            .append(Text.literal(config.locationTimeout + "ms").formatted(Formatting.WHITE)));
        
        return 1;
    }
    
    private static int setCoordinateCheckInterval(CommandContext<FabricClientCommandSource> context) {
        int ms = IntegerArgumentType.getInteger(context, "ms");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.coordinateCheckInterval = ms;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Coordinate check interval set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(ms + "ms").formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setRealmCheckIntervalSpawn(CommandContext<FabricClientCommandSource> context) {
        int ms = IntegerArgumentType.getInteger(context, "ms");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.realmCheckIntervalSpawn = ms;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Realm check interval (spawn) set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(ms + "ms").formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setRealmCheckIntervalOverworld(CommandContext<FabricClientCommandSource> context) {
        int ms = IntegerArgumentType.getInteger(context, "ms");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.realmCheckIntervalOverworld = ms;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Realm check interval (overworld) set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(ms + "ms").formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setPlayerMonitorInterval(CommandContext<FabricClientCommandSource> context) {
        int ms = IntegerArgumentType.getInteger(context, "ms");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.playerMonitorInterval = ms;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Player monitor interval set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(ms + "ms").formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setLocationTimeout(CommandContext<FabricClientCommandSource> context) {
        int ms = IntegerArgumentType.getInteger(context, "ms");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.locationTimeout = ms;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Location timeout set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(ms + "ms").formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int resetTimingSettings(CommandContext<FabricClientCommandSource> context) {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.coordinateCheckInterval = 500;
        config.realmCheckIntervalSpawn = 200;
        config.realmCheckIntervalOverworld = 30000;
        config.playerMonitorInterval = 5000;
        config.locationTimeout = 45000;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] All timing settings reset to defaults").formatted(Formatting.GREEN))
        );
        
        return 1;
    }
    
    private static int setPlayerListMonitoring(CommandContext<FabricClientCommandSource> context, boolean enabled) {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.enablePlayerListMonitoring = enabled;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Player list monitoring ").formatted(Formatting.GRAY))
                .append(Text.literal(enabled ? "ENABLED" : "DISABLED")
                    .formatted(enabled ? Formatting.GREEN : Formatting.RED))
        );
        
        return 1;
    }
    
    private static int setNearbyPlayerDetection(CommandContext<FabricClientCommandSource> context, boolean enabled) {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.enableNearbyPlayerDetection = enabled;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Nearby player detection ").formatted(Formatting.GRAY))
                .append(Text.literal(enabled ? "ENABLED" : "DISABLED")
                    .formatted(enabled ? Formatting.GREEN : Formatting.RED))
        );
        
        return 1;
    }
    
    private static int setNearbyPlayerDetectionRadius(CommandContext<FabricClientCommandSource> context) {
        double blocks = DoubleArgumentType.getDouble(context, "blocks");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.nearbyPlayerDetectionRadius = blocks;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Nearby player detection radius set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(String.format("%.1f blocks", blocks)).formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int addPlayerToAvoid(CommandContext<FabricClientCommandSource> context) {
        String player = StringArgumentType.getString(context, "player");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        List<String> playersToAvoid = new ArrayList<>(Arrays.asList(config.playersToAvoid));
        if (playersToAvoid.contains(player)) {
            context.getSource().sendError(Text.literal(player + " is already in the avoid list"));
            return 0;
        }
        
        playersToAvoid.add(player);
        config.playersToAvoid = playersToAvoid.toArray(new String[0]);
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Added ").formatted(Formatting.GRAY))
                .append(Text.literal(player).formatted(Formatting.WHITE))
                .append(Text.literal(" to avoid list").formatted(Formatting.RED))
        );
        
        return 1;
    }
    
    private static int removePlayerToAvoid(CommandContext<FabricClientCommandSource> context) {
        String player = StringArgumentType.getString(context, "player");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        List<String> playersToAvoid = new ArrayList<>(Arrays.asList(config.playersToAvoid));
        if (playersToAvoid.remove(player)) {
            config.playersToAvoid = playersToAvoid.toArray(new String[0]);
            ConfigManager.updateConfig(config);
            PokeAlertClient.getInstance().reloadConfig();
            
            context.getSource().sendFeedback(
                Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                    .append(Text.literal("] Removed ").formatted(Formatting.GRAY))
                    .append(Text.literal(player).formatted(Formatting.WHITE))
                    .append(Text.literal(" from avoid list").formatted(Formatting.GRAY))
            );
        } else {
            context.getSource().sendError(Text.literal(player + " is not in the avoid list"));
        }
        
        return 1;
    }
    
    private static int listPlayersToAvoid(CommandContext<FabricClientCommandSource> context) {
        PokeAlertConfig config = ConfigManager.getConfig();
        FabricClientCommandSource source = context.getSource();
        
        source.sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Players to Avoid ").formatted(Formatting.GRAY))
                .append(Text.literal("(" + config.playersToAvoid.length + ")").formatted(Formatting.WHITE))
        );
        
        if (config.playersToAvoid.length == 0) {
            source.sendFeedback(Text.literal("  No players in avoid list").formatted(Formatting.GRAY));
        } else {
            for (String player : config.playersToAvoid) {
                source.sendFeedback(Text.literal("  • ").formatted(Formatting.GRAY)
                    .append(Text.literal(player).formatted(Formatting.WHITE)));
            }
        }
        
        return 1;
    }
    
    private static int setInitialQueueSize(CommandContext<FabricClientCommandSource> context) {
        int size = IntegerArgumentType.getInteger(context, "size");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.initialQueueSize = size;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Initial queue size set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(String.valueOf(size)).formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setReplenishCount(CommandContext<FabricClientCommandSource> context) {
        int count = IntegerArgumentType.getInteger(context, "count");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.replenishCount = count;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Replenish count set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(String.valueOf(count)).formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setLocationsForStep5(CommandContext<FabricClientCommandSource> context) {
        int count = IntegerArgumentType.getInteger(context, "count");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.locationsForStep5 = count;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Locations for completion set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(String.valueOf(count)).formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setMaxConsecutiveTimeouts(CommandContext<FabricClientCommandSource> context) {
        int max = IntegerArgumentType.getInteger(context, "max");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.maxConsecutiveTimeouts = max;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Max consecutive timeouts set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(String.valueOf(max)).formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    // ========== Phase 3: Human-like Behavior Commands ==========
    
    private static int setHumanLikeBehavior(CommandContext<FabricClientCommandSource> context, boolean enabled) {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.enableHumanLikeBehavior = enabled;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Human-like behavior ").formatted(Formatting.GRAY))
                .append(Text.literal(enabled ? "ENABLED" : "DISABLED")
                    .formatted(enabled ? Formatting.GREEN : Formatting.RED))
        );
        
        return 1;
    }
    
    private static int showBehaviorStatus(CommandContext<FabricClientCommandSource> context) {
        PokeAlertConfig config = ConfigManager.getConfig();
        FabricClientCommandSource source = context.getSource();
        
        source.sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Human-like Behavior Settings").formatted(Formatting.WHITE))
        );
        
        source.sendFeedback(formatCategoryStatus("Enabled", config.enableHumanLikeBehavior));
        source.sendFeedback(Text.literal("  Long Pause: ").formatted(Formatting.GRAY)
            .append(Text.literal(config.minLongPauseMs + "-" + config.maxLongPauseMs + "ms").formatted(Formatting.WHITE))
            .append(Text.literal(" (").formatted(Formatting.GRAY))
            .append(Text.literal(String.format("%.1f%%", config.longPauseChance * 100)).formatted(Formatting.WHITE))
            .append(Text.literal(")").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("  Break Pause: ").formatted(Formatting.GRAY)
            .append(Text.literal(config.minBreakPauseMs + "-" + config.maxBreakPauseMs + "ms").formatted(Formatting.WHITE))
            .append(Text.literal(" (").formatted(Formatting.GRAY))
            .append(Text.literal(String.format("%.1f%%", config.breakPauseChance * 100)).formatted(Formatting.WHITE))
            .append(Text.literal(")").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("  Backtrack: ").formatted(Formatting.GRAY)
            .append(Text.literal(String.format("%.1f%%", config.backtrackChance * 100)).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  Walk: ").formatted(Formatting.GRAY)
            .append(Text.literal(String.format("%.1f%%", config.walkChance * 100)).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  Hotbar Switch: ").formatted(Formatting.GRAY)
            .append(Text.literal(String.format("%.1f%%", config.hotbarSwitchChance * 100)).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  Jump While Moving: ").formatted(Formatting.GRAY)
            .append(Text.literal(String.format("%.2f%%", config.jumpWhileMovingChance * 100)).formatted(Formatting.WHITE)));
        source.sendFeedback(Text.literal("  Look Around: ").formatted(Formatting.GRAY)
            .append(Text.literal(String.format("%.1f%%", config.lookAroundChance * 100)).formatted(Formatting.WHITE)));
        
        return 1;
    }
    
    private static int resetBehaviorSettings(CommandContext<FabricClientCommandSource> context) {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.enableHumanLikeBehavior = true;
        config.minLongPauseMs = 5000;
        config.maxLongPauseMs = 15000;
        config.minBreakPauseMs = 30000;
        config.maxBreakPauseMs = 60000;
        config.longPauseChance = 0.05;
        config.breakPauseChance = 0.01;
        config.backtrackChance = 0.04;
        config.walkChance = 0.15;
        config.hotbarSwitchChance = 0.05;
        config.jumpWhileMovingChance = 0.0005;
        config.lookAroundChance = 0.03;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] All behavior settings reset to defaults").formatted(Formatting.GREEN))
        );
        
        return 1;
    }
    
    private static int setLongPauseRange(CommandContext<FabricClientCommandSource> context) {
        int minMs = IntegerArgumentType.getInteger(context, "minMs");
        int maxMs = IntegerArgumentType.getInteger(context, "maxMs");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        if (minMs >= maxMs) {
            context.getSource().sendError(Text.literal("Minimum must be less than maximum"));
            return 0;
        }
        
        config.minLongPauseMs = minMs;
        config.maxLongPauseMs = maxMs;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Long pause range set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(minMs + "-" + maxMs + "ms").formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setBreakPauseRange(CommandContext<FabricClientCommandSource> context) {
        int minMs = IntegerArgumentType.getInteger(context, "minMs");
        int maxMs = IntegerArgumentType.getInteger(context, "maxMs");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        if (minMs >= maxMs) {
            context.getSource().sendError(Text.literal("Minimum must be less than maximum"));
            return 0;
        }
        
        config.minBreakPauseMs = minMs;
        config.maxBreakPauseMs = maxMs;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Break pause range set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(minMs + "-" + maxMs + "ms").formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setLongPauseChance(CommandContext<FabricClientCommandSource> context) {
        double percent = DoubleArgumentType.getDouble(context, "percent");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.longPauseChance = percent / 100.0;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Long pause chance set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(String.format("%.1f%%", percent)).formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setBreakPauseChance(CommandContext<FabricClientCommandSource> context) {
        double percent = DoubleArgumentType.getDouble(context, "percent");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.breakPauseChance = percent / 100.0;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Break pause chance set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(String.format("%.1f%%", percent)).formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setBacktrackChance(CommandContext<FabricClientCommandSource> context) {
        double percent = DoubleArgumentType.getDouble(context, "percent");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.backtrackChance = percent / 100.0;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Backtrack chance set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(String.format("%.1f%%", percent)).formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setWalkChance(CommandContext<FabricClientCommandSource> context) {
        double percent = DoubleArgumentType.getDouble(context, "percent");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.walkChance = percent / 100.0;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Walk chance set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(String.format("%.1f%%", percent)).formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setHotbarSwitchChance(CommandContext<FabricClientCommandSource> context) {
        double percent = DoubleArgumentType.getDouble(context, "percent");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.hotbarSwitchChance = percent / 100.0;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Hotbar switch chance set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(String.format("%.1f%%", percent)).formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setJumpWhileMovingChance(CommandContext<FabricClientCommandSource> context) {
        double percent = DoubleArgumentType.getDouble(context, "percent");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.jumpWhileMovingChance = percent / 100.0;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Jump while moving chance set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(String.format("%.2f%%", percent)).formatted(Formatting.WHITE))
        );
        
        return 1;
    }
    
    private static int setLookAroundChance(CommandContext<FabricClientCommandSource> context) {
        double percent = DoubleArgumentType.getDouble(context, "percent");
        PokeAlertConfig config = ConfigManager.getConfig();
        
        config.lookAroundChance = percent / 100.0;
        ConfigManager.updateConfig(config);
        PokeAlertClient.getInstance().reloadConfig();
        
        context.getSource().sendFeedback(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                .append(Text.literal("] Look around chance set to: ").formatted(Formatting.GRAY))
                .append(Text.literal(String.format("%.1f%%", percent)).formatted(Formatting.WHITE))
        );
        
        return 1;
    }
}
