/******************************************************************************
 * Multiverse 2 Copyright (c) the Multiverse Team 2011.                       *
 * Multiverse 2 is licensed under the BSD License.                            *
 * For more information please check the README.md file included              *
 * with this project.                                                         *
 ******************************************************************************/

package com.onarandombox.MultiverseCore.test.utils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.mockito.ArgumentMatcher;
import org.mockito.Matchers;

public class MockWorldFactory {

    private static final Map<String, World> createdWorlds = new HashMap<String, World>();

    private MockWorldFactory() {
    }

    private static class LocationMatcher extends ArgumentMatcher<Location> {
        private Location l;

        public LocationMatcher(Location location) {
            this.l = location;
        }

        public boolean matches(Object creator) {
            return creator.equals(l);
        }
    }

    private static class LocationMatcherAbove extends LocationMatcher {

        public LocationMatcherAbove(Location location) {
            super(location);
        }

        public boolean matches(Object creator) {
            Util.log("Checking above...");
            if (super.l == null || creator == null) {
                return false;
            }
            boolean equal = ((Location) creator).getBlockY() >= super.l.getBlockY();
            Util.log("Checking equals/\\..." + equal);
            return equal;
        }
    }

    private static class LocationMatcherBelow extends LocationMatcher {

        public LocationMatcherBelow(Location location) {
            super(location);
        }

        public boolean matches(Object creator) {
            if (super.l == null || creator == null) {
                return false;
            }
            boolean equal = ((Location) creator).getBlockY() < super.l.getBlockY();
            Util.log("Checking equals\\/..." + equal);
            return equal;
        }
    }

    private static void registerWorld(World world) {
        createdWorlds.put(world.getName(), world);
    }

    private static World basics(String world, World.Environment env) {
        World mockWorld = mock(World.class);
        when(mockWorld.getName()).thenReturn(world);
        when(mockWorld.getEnvironment()).thenReturn(env);
        when(mockWorld.getSpawnLocation()).thenReturn(new Location(mockWorld, 0, 0, 0));
        LocationMatcherAbove matchWorldAbove = new LocationMatcherAbove(new Location(mockWorld, 0, 0, 0));
        LocationMatcherBelow matchWorldBelow = new LocationMatcherBelow(new Location(mockWorld, 0, 0, 0));
        when(mockWorld.getBlockAt(Matchers.argThat(matchWorldAbove))).thenReturn(new MockBlock(new Location(mockWorld, 0, 0, 0), Material.AIR));
        when(mockWorld.getBlockAt(Matchers.argThat(matchWorldBelow))).thenReturn(new MockBlock(new Location(mockWorld, 0, 0, 0), Material.STONE));
        return mockWorld;
    }

    public static World makeNewMockWorld(String world, World.Environment env) {
        World w = basics(world, env);
        registerWorld(w);
        return w;
    }

    public static World makeNewMockWorld(String world, World.Environment env, long seed,
            ChunkGenerator generator) {
        World mockWorld = basics(world, env);
        when(mockWorld.getGenerator()).thenReturn(generator);
        when(mockWorld.getSeed()).thenReturn(seed);
        registerWorld(mockWorld);
        return mockWorld;
    }

    public static World getWorld(String name) {
        return createdWorlds.get(name);
    }

    public static List<World> getWorlds() {
        return new ArrayList<World>(createdWorlds.values());
    }
}
