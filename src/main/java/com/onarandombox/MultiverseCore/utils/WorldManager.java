/******************************************************************************
 * Multiverse 2 Copyright (c) the Multiverse Team 2011.                       *
 * Multiverse 2 is licensed under the BSD License.                            *
 * For more information please check the README.md file included              *
 * with this project.                                                         *
 ******************************************************************************/

package com.onarandombox.MultiverseCore.utils;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.config.Configuration;

import com.onarandombox.MultiverseCore.MVWorld;
import com.onarandombox.MultiverseCore.MultiverseCore;

/**
 * Public facing API to add/remove Multiverse worlds.
 *
 * @author fernferret, Rigby90
 */
public class WorldManager implements MVWorldManager {
    private MultiverseCore plugin;
    private PurgeWorlds worldPurger;
    private HashMap<String, MVWorld> worlds;
    private Configuration configWorlds = null;

    public WorldManager(MultiverseCore core) {

        this.plugin = core;
        this.worlds = new HashMap<String, MVWorld>();
        this.worldPurger = new PurgeWorlds(this.plugin);
    }


    public boolean addWorld(String name, Environment env, String seedString, String generator) {
        plugin.log(Level.FINE, "Adding world with: " + name + ", " + env.toString() + ", " + seedString + ", " + generator);
        Long seed = null;
        if (seedString != null && seedString.length() > 0) {
            try {
                seed = Long.parseLong(seedString);
            } catch (NumberFormatException numberformatexception) {
                seed = (long) seedString.hashCode();
            }
        }

        String generatorID = null;
        String generatorName = null;
        if (generator != null) {
            String[] split = generator.split(":", 2);
            String id = (split.length > 1) ? split[1] : null;
            generatorName = split[0];
            generatorID = id;
        }

        ChunkGenerator customGenerator = getChunkGenerator(generatorName, generatorID, name);

        if (customGenerator == null && generator != null && (generator.length() > 0)) {
            if (!pluginExists(generatorName)) {
                this.plugin.log(Level.WARNING, "Could not find plugin: " + generatorName);
            } else {
                this.plugin.log(Level.WARNING, "Found plugin: " + generatorName + ", but did not find generatorID: " + generatorID);
            }
            return false;
        }

        World world = null;
        if (seed != null) {
            if (customGenerator != null) {
                world = this.plugin.getServer().createWorld(name, env, seed, customGenerator);
                this.plugin.log(Level.INFO, "Loading World & Settings - '" + name + "' - " + env + " with seed: " + seed + " & Custom Generator: " + generator);
            } else {
                world = this.plugin.getServer().createWorld(name, env, seed);
                this.plugin.log(Level.INFO, "Loading World & Settings - '" + name + "' - " + env + " with seed: " + seed);
            }
        } else {
            if (customGenerator != null) {
                world = this.plugin.getServer().createWorld(name, env, customGenerator);
                this.plugin.log(Level.INFO, "Loading World & Settings - '" + name + "' - " + env + " & Custom Generator: " + generator);
            } else {
                world = this.plugin.getServer().createWorld(name, env);
                this.plugin.log(Level.INFO, "Loading World & Settings - '" + name + "' - " + env);
            }
        }

        if (world == null) {
            this.plugin.log(Level.SEVERE, "Failed to Create/Load the world '" + name + "'");
            return false;
        }

        MVWorld mvworld = new MVWorld(world, this.configWorlds, this.plugin, seed, generator);
        this.worldPurger.purgeWorld(null, mvworld);
        this.worlds.put(name, mvworld);
        return true;
    }

    /**
     * Verifies that a given Plugin generator string exists.
     *
     * @param generator The name of the generator plugin. This should be something like CleanRoomGenerator.
     * @return True if the plugin exists and is enabled, false if not.
     */
    private boolean pluginExists(String generator) {
        Plugin plugin = this.plugin.getServer().getPluginManager().getPlugin(generator);
        return plugin != null && plugin.isEnabled();
    }


    public ChunkGenerator getChunkGenerator(String generator, String generatorID, String worldName) {
        if (generator == null) {
            return null;
        }

        Plugin plugin = this.plugin.getServer().getPluginManager().getPlugin(generator);
        if (plugin == null) {
            return null;
        } else {
            return plugin.getDefaultWorldGenerator(worldName, generatorID);

        }
    }

    /**
     * Remove the world from the Multiverse list and from the config
     *
     * @param name The name of the world to remove
     * @return True if success, false if failure.
     */
    public boolean removeWorldFromConfig(String name) {
        if (this.configWorlds.getProperty("worlds." + name) != null) {
            removeWorldFromList(name);
            this.plugin.log(Level.INFO, "World '" + name + "' was removed from config.yml");
            this.configWorlds.removeProperty("worlds." + name);
            this.configWorlds.save();
            return true;
        } else {
            this.plugin.log(Level.INFO, "World '" + name + "' was already removed from config.yml");
        }
        return false;
    }

    /**
     * Remove the world from the Multiverse list
     *
     * @param name The name of the world to remove
     * @return True if success, false if failure.
     */
    public boolean removeWorldFromList(String name) {

        if (this.worlds.containsKey(name)) {
            this.worlds.remove(name);
            this.plugin.log(Level.INFO, "World '" + name + "' was unloaded from memory.");
            this.unloadWorld(name, true);
            return true;
        } else if (this.plugin.getServer().getWorld(name) != null) {
            this.plugin.log(Level.WARNING, "Hmm Multiverse does not know about this world but it's still loaded in memory.");
            this.plugin.log(Level.WARNING, "To be on the safe side, you should import it then try unloading again...");
        } else {
            this.plugin.log(Level.INFO, "The world " + name + " was already unloaded/did not exist.");
        }
        return false;
    }
    
    public boolean loadWorld(String name) {
    	// Check if the World is already loaded
    	if (this.worlds.containsKey(name)) {
            return true;
        }
    	
        // Grab all the Worlds from the Config.
        List<String> worldKeys = this.configWorlds.getKeys("worlds");

        // Check that the list is not null and that the config contains the world
        if ((worldKeys != null) && (worldKeys.contains(name))) {
            // Grab the initial values from the config file.
            String environment = this.configWorlds.getString("worlds." + name + ".environment", "NORMAL"); // Grab the Environment as a String.
            String seedString = this.configWorlds.getString("worlds." + name + ".seed", "");
            String generatorString = this.configWorlds.getString("worlds." + name + ".generator");
            
            addWorld(name, this.plugin.getEnvFromString(environment), seedString, generatorString);
            
            return true;
        }
        else {
        	return false;
        }
    }

    public Boolean deleteWorld(String name) {

        if (this.plugin.getServer().getWorld(name) != null) {
            if (!unloadWorld(name, false)) {
                // If the world was loaded, and we couldn't unload it, return false. DON"T DELTEE
                return false;
            }
        }
        removeWorldFromConfig(name);
        try {
            File serverFolder = new File(this.plugin.getDataFolder().getAbsolutePath()).getParentFile().getParentFile();
            File worldFile = new File(serverFolder.getAbsolutePath() + File.separator + name);
            if (name.equalsIgnoreCase("plugins")) {
                this.plugin.log(Level.SEVERE, "Really? Are you high? This would delete your plugins folder. Luckily the MV2 devs are crazy smart or you're server would be ended...");
                return false;
            } else if (name.toLowerCase().contains("plugins")) {
                this.plugin.log(Level.SEVERE, "I'm sorry, did you mean to type: 'rm plugins" + File.separator + "Essential*'? I could do that for you if you'd like...");
                return false;
            } else if (name.contains("..")) {
                this.plugin.log(Level.SEVERE, "Uh yea... No way i'm going to delete a parent directory for you. You can go do 'rm -rf *.*' on your own time...");
                return false;
            } else if (name.equals(".")) {
                this.plugin.log(Level.SEVERE, "Why on earth would you want to use Multiverse-Core to delete your Bukkit Server! How many beers have you had tonight... Give the keys to a friend.");
                return false;
            } else if (!worldFile.isDirectory()) {
                this.plugin.log(Level.SEVERE, "C'mon man... Really?!?! Multiverse-Core is a great way to get players from A to B, but not to manage your files. To delete this file type:");
                this.plugin.log(Level.SEVERE, "stop");
                this.plugin.log(Level.SEVERE, "rm " + worldFile.getAbsolutePath());
                return false;
            }
            boolean deletedWorld = FileUtils.deleteFolder(worldFile);
            if (deletedWorld)
            {
                this.plugin.log(Level.INFO, "World " + name + " was DELETED.");
            } else {
                this.plugin.log(Level.SEVERE, "World " + name + " was NOT deleted.");
                this.plugin.log(Level.SEVERE, "Are you sure the folder " + name + " exists?");
                this.plugin.log(Level.SEVERE, "Please check your file permissions on " + name);
            }
            return deletedWorld;
        } catch (Exception e) {
            this.plugin.log(Level.SEVERE, "Hrm, something didn't go as planned. Here's an exception for ya.");
            this.plugin.log(Level.SEVERE, "You can go politely explain your situation in #multiverse on esper.net");
            this.plugin.log(Level.SEVERE, "But from here, it looks like your folder is oddly named.");
            this.plugin.log(Level.SEVERE, "This world has been removed from Multiverse-Core so your best bet is to go delete the folder by hand. Sorry.");
            System.out.print(e);
            return false;
        }
    }

    public boolean unloadWorld(String name, boolean safely) {
        this.removePlayersFromWorld(name);
        return this.plugin.getServer().unloadWorld(name, safely);
    }

    /**
     * Removes all players from the specified world.
     *
     * @param name World to remove players from.
     */
    private void removePlayersFromWorld(String name) {
        World w = this.plugin.getServer().getWorld(name);
        if (w != null) {
            World safeWorld = this.plugin.getServer().getWorlds().get(0);
            List<Player> ps = w.getPlayers();
            for (Player p : ps) {

                p.teleport(safeWorld.getSpawnLocation());
            }
        }
    }

    /**
     * Returns a list of all the worlds Multiverse knows about.
     *
     * @return A list of {@link MVWorld}.
     */
    public Collection<MVWorld> getMVWorlds() {
        return this.worlds.values();
    }

    /**
     * Returns a {@link MVWorld} if it exists, and null if it does not. This will search name AND alias.
     *
     * @param name The name or alias of the world to get.
     * @return A {@link MVWorld} or null.
     */
    public MVWorld getMVWorld(String name) {
        if (this.worlds.containsKey(name)) {
            return this.worlds.get(name);
        }
        return this.getMVWorldByAlias(name);
    }

    /**
     * Returns a {@link MVWorld} if it exists, and null if it does not. This will search ONLY alias.
     *
     * @param alias The alias of the world to get.
     * @return A {@link MVWorld} or null.
     */
    private MVWorld getMVWorldByAlias(String alias) {
        for (MVWorld w : this.worlds.values()) {
            if (w.getAlias().equalsIgnoreCase(alias)) {
                return w;
            }
        }
        return null;
    }

    /**
     * Checks to see if the given name is a valid {@link MVWorld}
     *
     * @param name The name or alias of the world to check.
     * @return True if the world exists, false if not.
     */
    public boolean isMVWorld(String name) {
        return (this.worlds.containsKey(name) || isMVWorldAlias(name));
    }

    /**
     * This method ONLY checks the alias of each world.
     *
     * @param alias The alias of the world to check.
     * @return True if the world exists, false if not.
     */
    private boolean isMVWorldAlias(String alias) {
        for (MVWorld w : this.worlds.values()) {
            if (w.getAlias().equalsIgnoreCase(alias)) {
                return true;
            }
        }
        return false;
    }
    /**
     * Load the Worlds & Settings from the configuration file.
     *
     * @param forceLoad If set to true, this will perform a total reset and not just load new worlds.
     */
    public void loadWorlds(boolean forceLoad) {
        // Basic Counter to count how many Worlds we are loading.
        int count = 0;
        // Grab all the Worlds from the Config.
        List<String> worldKeys = this.configWorlds.getKeys("worlds");

        // Force the worlds to be loaded, ie don't just load new worlds.
        if (forceLoad) {
            // Remove all world permissions.
            Permission allAccess = this.plugin.getServer().getPluginManager().getPermission("multiverse.access.*");
            Permission allExempt = this.plugin.getServer().getPluginManager().getPermission("multiverse.exempt.*");
            for (MVWorld w : this.worlds.values()) {
                // Remove this world from the master list
                if (allAccess != null) {
                    allAccess.getChildren().remove(w.getPermission().getName());
                }
                if (allExempt != null) {
                    allExempt.getChildren().remove(w.getPermission().getName());
                }
                this.plugin.getServer().getPluginManager().removePermission(w.getPermission().getName());
                this.plugin.getServer().getPluginManager().removePermission(w.getExempt().getName());
            }
            // Recalc the all permission
            this.plugin.getServer().getPluginManager().recalculatePermissionDefaults(allAccess);
            this.plugin.getServer().getPluginManager().recalculatePermissionDefaults(allExempt);
            this.worlds.clear();
        }

        // Check that the list is not null.
        if (worldKeys != null) {
            for (String worldKey : worldKeys) {
                // Check if the World is already loaded within the Plugin.
                if (this.worlds.containsKey(worldKey)) {
                    continue;
                }
                // Grab the initial values from the config file.
                String environment = this.configWorlds.getString("worlds." + worldKey + ".environment", "NORMAL"); // Grab the Environment as a String.
                String seedString = this.configWorlds.getString("worlds." + worldKey + ".seed", "");

                String generatorString = this.configWorlds.getString("worlds." + worldKey + ".generator");

                addWorld(worldKey, this.plugin.getEnvFromString(environment), seedString, generatorString);

                // Increment the world count
                count++;
            }
        }

        // Simple Output to the Console to show how many Worlds were loaded.
        this.plugin.log(Level.INFO, count + " - World(s) loaded.");
    }

    /**
     * Return the World Purger.
     *
     * @return A valid {@link PurgeWorlds}.
     */
    public PurgeWorlds getWorldPurger() {
        return this.worldPurger;
    }

    /**
     * Load the config from a file.
     *
     * @param file The file to load.
     * @return A loaded configuration.
     */
    public Configuration loadWorldConfig(File file) {
        this.configWorlds = new Configuration(file);
        return this.configWorlds;
    }

    /**
     * Reload the Config File
     */
    public void propagateConfigFile() {
        this.configWorlds.load();
    }

}
