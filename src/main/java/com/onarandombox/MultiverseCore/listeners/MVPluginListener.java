/******************************************************************************
 * Multiverse 2 Copyright (c) the Multiverse Team 2011.                       *
 * Multiverse 2 is licensed under the BSD License.                            *
 * For more information please check the README.md file included              *
 * with this project.                                                         *
 ******************************************************************************/

package com.onarandombox.MultiverseCore.listeners;

import com.fernferret.allpay.AllPay;
import com.onarandombox.MultiverseCore.MultiverseCore;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerListener;

import java.util.Arrays;
import java.util.logging.Level;

public class MVPluginListener extends ServerListener {

    MultiverseCore plugin;

    public MVPluginListener(MultiverseCore plugin) {
        this.plugin = plugin;
    }

    /** Keep an eye out for Plugins which we can utilize. */
    @SuppressWarnings("deprecation") //we need to use the deprecated MVPlugin here, it's not our fault and we know that it's deprecated
    @Override
    public void onPluginEnable(PluginEnableEvent event) {
        if(event.getPlugin() instanceof com.onarandombox.MultiverseCore.MVPlugin) {
            this.plugin.log(Level.SEVERE, "Your version of '" + event.getPlugin() + "' is OUT OF DATE.");
            this.plugin.log(Level.SEVERE, "Please grab the latest version from:");
            this.plugin.log(Level.SEVERE, "http://bukkit.onarandombox.com/?dir="+event.getPlugin().getDescription().getName().toLowerCase());
            this.plugin.log(Level.SEVERE, "I'm going to disable " + event.getPlugin().getDescription().getName() + " now.");
            this.plugin.log(Level.SEVERE, "IF YOU DO NOT UPDATE, YOUR SERVER WILL **NOT** FUNCTION PROPERLY!!!");
            this.plugin.getServer().getPluginManager().disablePlugin(event.getPlugin());
        }
        // Let AllPay handle all econ plugin loadings, only go for econ plugins we support
        if (Arrays.asList(AllPay.validEconPlugins).contains(event.getPlugin().getDescription().getName())) {
            this.plugin.setBank(this.plugin.getBanker().loadEconPlugin());
        }
        if (event.getPlugin().getDescription().getName().equals("MultiVerse")) {
            if (event.getPlugin().isEnabled()) {
                this.plugin.getServer().getPluginManager().disablePlugin(event.getPlugin());
                this.plugin.log(Level.WARNING, "I just disabled the old version of Multiverse for you. You should remove the JAR now, your configs have been migrated.");
            }
        }
        if (event.getPlugin().getDescription().getName().equals("Spout")) {
            this.plugin.setSpout();
            this.plugin.log(Level.INFO, "Spout integration enabled.");
        }
    }

    /** We'll check if any of the plugins we rely on decide to Disable themselves. */
    @Override
    public void onPluginDisable(PluginDisableEvent event) {
        // TODO: Disable econ when it disables.
    }

}
