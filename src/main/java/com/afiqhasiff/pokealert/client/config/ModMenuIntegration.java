package com.afiqhasiff.pokealert.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Integration with Mod Menu for in-game configuration.
 * This class is loaded by Mod Menu when present.
 */
public class ModMenuIntegration implements ModMenuApi {
    
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new PokeAlertConfigScreen(parent);
    }
}

