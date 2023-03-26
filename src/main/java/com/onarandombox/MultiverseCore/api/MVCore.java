/******************************************************************************
 * Multiverse 2 Copyright (c) the Multiverse Team 2011.                       *
 * Multiverse 2 is licensed under the BSD License.                            *
 * For more information please check the README.md file included              *
 * with this project.                                                         *
 ******************************************************************************/

package com.onarandombox.MultiverseCore.api;

/**
 * Multiverse 2 Core API
 * <p>
 * This API contains a bunch of useful things you can get out of Multiverse in general!
 */
public interface MVCore extends MVPlugin {

    /**
     * Reloads the Multiverse Configuration files:
     * worlds.yml and config.yml.
     */
    void loadConfigs();

    /**
     * Saves the Multiverse-Config.
     *
     * @return Whether the Multiverse-Config was successfully saved
     */
    boolean saveMVConfig();

    /**
     * Saves all configs.
     *
     * @return Whether the config was successfully saved
     */
    boolean saveAllConfigs();

    /**
     * Decrements the number of plugins that have specifically hooked into core.
     */
    void decrementPluginCount();

    /**
     * Increments the number of plugins that have specifically hooked into core.
     */
    void incrementPluginCount();

    /**
     * Returns the number of plugins that have specifically hooked into core.
     *
     * @return The number if plugins that have hooked into core.
     */
    int getPluginCount();
}
