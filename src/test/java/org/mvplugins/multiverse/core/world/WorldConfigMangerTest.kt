package org.mvplugins.multiverse.core.world

import org.bukkit.Material
import org.bukkit.World.Environment
import org.bukkit.configuration.MemorySection
import org.bukkit.configuration.file.YamlConfiguration
import org.mvplugins.multiverse.core.TestWithMockBukkit
import org.mvplugins.multiverse.core.economy.MVEconomist
import org.mvplugins.multiverse.core.world.config.SpawnLocation
import org.mvplugins.multiverse.core.world.config.WorldsConfigManager
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.test.*

class WorldConfigMangerTest : TestWithMockBukkit() {

    private lateinit var worldConfigManager : WorldsConfigManager

    @BeforeTest
    fun setUp() {
        val defaultConfig = getResourceAsText("/default_worlds.yml")
        assertNotNull(defaultConfig)
        File(Path.of(multiverseCore.dataFolder.absolutePath, "worlds.yml").absolutePathString()).writeText(defaultConfig)

        worldConfigManager = serviceLocator.getActiveService(WorldsConfigManager::class.java).takeIf { it != null } ?: run {
            throw IllegalStateException("WorldsConfigManager is not available as a service") }
    }

    @Test
    fun `World config is loaded`() {
        assertTrue(worldConfigManager.isLoaded)
    }

    @Test
    fun `Old world config is migrated`() {
        val oldConfig = getResourceAsText("/old_worlds.yml")
        assertNotNull(oldConfig)
        File(Path.of(multiverseCore.dataFolder.absolutePath, "worlds.yml").absolutePathString()).writeText(oldConfig)

        assertTrue(worldConfigManager.load().isSuccess)
        assertTrue(worldConfigManager.save().isSuccess)

        val endWorldConfig = worldConfigManager.getWorldConfig("world_the_end").orNull
        assertNotNull(endWorldConfig)

        assertEquals("&aworld the end", endWorldConfig.alias)
        assertEquals(Environment.THE_END, endWorldConfig.environment)
        assertFalse(endWorldConfig.isEntryFeeEnabled)
        assertEquals(MVEconomist.VAULT_ECONOMY_MATERIAL, endWorldConfig.entryFeeCurrency)
        assertEquals(0.0, endWorldConfig.entryFeeAmount)

        val worldConfig = worldConfigManager.getWorldConfig("world").orNull
        assertNotNull(worldConfig)

        assertEquals(-5176596003035866649, worldConfig.seed)
        assertEquals(listOf("test"), worldConfig.worldBlacklist)
        assertTrue(worldConfig.isEntryFeeEnabled)
        assertEquals(Material.DIRT, worldConfig.entryFeeCurrency)
        assertEquals(5.0, worldConfig.entryFeeAmount)

        compareConfigFile("worlds.yml", "/migrated_worlds.yml")
    }

    @Test
    fun `Add a new world to config`() {
        assertTrue(worldConfigManager.load().isSuccess)
        val worldConfig = worldConfigManager.addWorldConfig("newworld")
        assertTrue(worldConfigManager.save().isSuccess)
        compareConfigFile("worlds.yml", "/newworld_worlds.yml")
    }

    @Test
    fun `Updating existing world properties`() {
        assertTrue(worldConfigManager.load().isSuccess)
        val worldConfig = worldConfigManager.getWorldConfig("world").orNull
        assertNotNull(worldConfig)

        worldConfig.stringPropertyHandle.setProperty("adjust-spawn", true)
        worldConfig.stringPropertyHandle.setProperty("alias", "newalias")
        worldConfig.stringPropertyHandle.setProperty("spawn-location", SpawnLocation(-64.0, 64.0, 48.0))
        assertTrue(worldConfigManager.save().isSuccess)
    }

    @Test
    fun `Delete world section from config`() {
        assertTrue(worldConfigManager.load().isSuccess)
        worldConfigManager.deleteWorldConfig("world")
        assertTrue(worldConfigManager.save().isSuccess)
        compareConfigFile("worlds.yml", "/delete_worlds.yml")
    }

    private fun compareConfigFile(actualPath: String, expectedPath: String) {
        val actualString = multiverseCore.dataFolder.toPath().resolve(actualPath).toFile().readText()
        val expectedString = getResourceAsText(expectedPath)
        assertNotNull(expectedString)

        val actualYaml = YamlConfiguration()
        actualYaml.loadFromString(actualString)
        val actualYamlKeys = HashSet(actualYaml.getKeys(true))

        val expectedYaml = YamlConfiguration()
        expectedYaml.loadFromString(expectedString)
        val expectedYamlKeys = HashSet(expectedYaml.getKeys(true))

        for (key in expectedYamlKeys) {
            assertNotNull(actualYamlKeys.remove(key), "Key $key is missing in actual config")
            val actualValue = actualYaml.get(key)
            if (actualValue is MemorySection) {
                continue
            }
            assertEquals(expectedYaml.get(key), actualYaml.get(key), "Value for $key is different.")
        }
        for (key in actualYamlKeys) {
            assertNull(actualYaml.get(key), "Key $key is present in actual config when it should be empty.")
        }

        assertEquals(0, actualYamlKeys.size,
            "Actual config has more keys than expected config. The following keys are missing: $actualYamlKeys")
    }
}
