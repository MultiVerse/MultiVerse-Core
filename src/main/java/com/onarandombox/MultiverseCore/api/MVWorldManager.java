/******************************************************************************
 * Multiverse 2 Copyright (c) the Multiverse Team 2011.                       *
 * Multiverse 2 is licensed under the BSD License.                            *
 * For more information please check the README.md file included              *
 * with this project.                                                         *
 ******************************************************************************/

package com.onarandombox.MultiverseCore.api;

import com.onarandombox.MultiverseCore.MVWorld;
import com.onarandombox.MultiverseCore.utils.PurgeWorlds;
import org.bukkit.World.Environment;
import org.bukkit.generator.ChunkGenerator;

import java.util.Collection;

/**
 * Multiverse 2 World Manager API
 * <p/>
 * This API contains all of the world managing
 * functions that your heart desires!
 */
public interface MVWorldManager {
    /**
     * Add a new World to the Multiverse Setup.
     *
     * @param name       World Name
     * @param env        Environment Type
     * @param seedString The seed in the form of a string.
     *                   If the seed is a Long,
     *                   it will be interpreted as such.
     * @param generator  The Custom generator plugin to use.
     *
     * @return True if the world is added, false if not.
     */
    public boolean addWorld(String name, Environment env, String seedString, String generator);

    /**
     * Remove the world from the Multiverse list, from the
     * config and deletes the folder
     *
     * @param name The name of the world to remove
     *
     * @return True if success, false if failure.
     */
    public Boolean deleteWorld(String name);

    /**
     * Unload a world from Multiverse
     *
     * @param name Name of the world to unload
     *
     * @return True if the world was unloaded, false if not.
     */
    public boolean unloadWorld(String name);

    /**
     * Loads the world. Only use this if the world has been
     * unloaded with {@link #unloadWorld(String)}.
     *
     * @param name The name of the world to load
     *
     * @return True if success, false if failure.
     */
    public boolean loadWorld(String name);
    
    /**
     * Removes all players from the specified world.
     *
     * @param name World to remove players from.
     */
    public void removePlayersFromWorld(String name);
    
    /**
     * Test if a given chunk generator is valid.
     *
     * @param generator   The generator name.
     * @param generatorID The generator id.
     * @param worldName   The worldName to use as the default.
     *
     * @return A {@link ChunkGenerator} or null
     */
    public ChunkGenerator getChunkGenerator(String generator, String generatorID, String worldName);

    /**
     * Returns a list of all the worlds Multiverse knows about.
     *
     * @return A list of {@link MVWorld}.
     */
    public Collection<MVWorld> getMVWorlds();


    /**
     * Returns a {@link MVWorld} if it exists, and null if it does not.
     * This will search name AND alias.
     *
     * @param name The name or alias of the world to get.
     *
     * @return A {@link MVWorld} or null.
     */
    public MVWorld getMVWorld(String name);

    /**
     * Checks to see if the given name is a valid {@link MVWorld}
     *
     * @param name The name or alias of the world to check.
     *
     * @return True if the world exists, false if not.
     */
    public boolean isMVWorld(String name);

    /**
     * Load the Worlds & Settings from the configuration file.
     *
     * @param forceLoad If set to true, this will perform a total
     *                  reset and not just load new worlds.
     */
    public void loadWorlds(boolean forceLoad);

    /**
     * Return the World Purger.
     *
     * @return A valid {@link PurgeWorlds}.
     */
    public PurgeWorlds getWorldPurger();
}
