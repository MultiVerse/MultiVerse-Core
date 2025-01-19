package org.mvplugins.multiverse.core.world;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.dumptruckman.minecraft.util.Logging;
import com.google.common.base.Strings;
import io.vavr.control.Option;
import io.vavr.control.Try;
import jakarta.inject.Inject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jvnet.hk2.annotations.Service;

import org.mvplugins.multiverse.core.api.teleportation.BlockSafety;
import org.mvplugins.multiverse.core.api.teleportation.LocationManipulation;
import org.mvplugins.multiverse.core.api.world.LoadedMultiverseWorld;
import org.mvplugins.multiverse.core.api.world.MultiverseWorld;
import org.mvplugins.multiverse.core.api.world.WorldManager;
import org.mvplugins.multiverse.core.api.event.MVWorldDeleteEvent;
import org.mvplugins.multiverse.core.permissions.CorePermissions;
import org.mvplugins.multiverse.core.api.locale.message.MessageReplacement;
import org.mvplugins.multiverse.core.api.result.Attempt;
import org.mvplugins.multiverse.core.api.result.FailureReason;
import org.mvplugins.multiverse.core.world.config.WorldConfig;
import org.mvplugins.multiverse.core.world.config.WorldsConfigManager;
import org.mvplugins.multiverse.core.api.world.generators.GeneratorProvider;
import org.mvplugins.multiverse.core.world.helpers.DataStore.GameRulesStore;
import org.mvplugins.multiverse.core.world.helpers.DataTransfer;
import org.mvplugins.multiverse.core.world.helpers.FilesManipulator;
import org.mvplugins.multiverse.core.api.world.options.CloneWorldOptions;
import org.mvplugins.multiverse.core.api.world.options.CreateWorldOptions;
import org.mvplugins.multiverse.core.api.world.options.ImportWorldOptions;
import org.mvplugins.multiverse.core.api.world.options.KeepWorldSettingsOptions;
import org.mvplugins.multiverse.core.api.world.options.RegenWorldOptions;
import org.mvplugins.multiverse.core.api.world.options.UnloadWorldOptions;
import org.mvplugins.multiverse.core.api.world.reasons.CloneFailureReason;
import org.mvplugins.multiverse.core.api.world.reasons.CreateFailureReason;
import org.mvplugins.multiverse.core.api.world.reasons.DeleteFailureReason;
import org.mvplugins.multiverse.core.api.world.reasons.ImportFailureReason;
import org.mvplugins.multiverse.core.api.world.reasons.LoadFailureReason;
import org.mvplugins.multiverse.core.api.world.reasons.RegenFailureReason;
import org.mvplugins.multiverse.core.api.world.reasons.RemoveFailureReason;
import org.mvplugins.multiverse.core.api.world.reasons.UnloadFailureReason;

import static org.mvplugins.multiverse.core.api.locale.message.MessageReplacement.replace;
import static org.mvplugins.multiverse.core.world.helpers.DataStore.WorldBorderStore;
import static org.mvplugins.multiverse.core.world.helpers.DataStore.WorldConfigStore;

@Service // SUPPRESS CHECKSTYLE: ClassFanOutComplexity This is the world manager, it's going to be complex.
public class SimpleWorldManager implements WorldManager {

    private static final List<String> CLONE_IGNORE_FILES = Arrays.asList("uid.dat", "session.lock");

    private final Map<String, SimpleMultiverseWorld> worldsMap;
    private final Map<String, SimpleMultiverseWorld> loadedWorldsMap;
    private final List<String> unloadTracker;
    private final List<String> loadTracker;
    private final WorldsConfigManager worldsConfigManager;
    private final WorldNameChecker worldNameChecker;
    private final GeneratorProvider generatorProvider;
    private final FilesManipulator filesManipulator;
    private final BlockSafety blockSafety;
    private final LocationManipulation locationManipulation;
    private final PluginManager pluginManager;
    private final CorePermissions corePermissions;

    @Inject
    SimpleWorldManager(
            @NotNull WorldsConfigManager worldsConfigManager,
            @NotNull WorldNameChecker worldNameChecker,
            @NotNull GeneratorProvider generatorProvider,
            @NotNull FilesManipulator filesManipulator,
            @NotNull BlockSafety blockSafety,
            @NotNull LocationManipulation locationManipulation,
            @NotNull PluginManager pluginManager,
            @NotNull CorePermissions corePermissions) {
        this.worldsMap = new HashMap<>();
        this.loadedWorldsMap = new HashMap<>();
        this.unloadTracker = new ArrayList<>();
        this.loadTracker = new ArrayList<>();

        this.worldsConfigManager = worldsConfigManager;
        this.worldNameChecker = worldNameChecker;
        this.generatorProvider = generatorProvider;
        this.filesManipulator = filesManipulator;
        this.blockSafety = blockSafety;
        this.locationManipulation = locationManipulation;
        this.pluginManager = pluginManager;
        this.corePermissions = corePermissions;
    }

    /**
     * Loads all worlds from the worlds config.
     *
     * @return The result of the load.
     */
    public Try<Void> initAllWorlds() {
        return updateWorldsFromConfig().andThenTry(() -> {
            loadDefaultWorlds();
            autoLoadWorlds();
            saveWorldsConfig();
        });
    }

    /**
     * Updates the current set of worlds to match the worlds config.
     *
     * @return A successful Try if the worlds.yml config was loaded successfully.
     */
    private Try<Void> updateWorldsFromConfig() {
        return worldsConfigManager.load().mapTry(result -> {
            loadNewWorldConfigs(result.newWorlds());
            removeWorldsNotInConfigs(result.removedWorlds());
            return null;
        });
    }

    private void loadNewWorldConfigs(Collection<WorldConfig> newWorldConfigs) {
        newWorldConfigs.forEach(worldConfig -> Option.of(worldsMap.get(worldConfig.getWorldName()))
                .peek(unloadedWorld ->  unloadedWorld.setWorldConfig(worldConfig))
                .onEmpty(() -> newMultiverseWorld(worldConfig.getWorldName(), worldConfig)));
    }

    private void removeWorldsNotInConfigs(Collection<String> removedWorlds) {
        removedWorlds.forEach(worldName -> removeWorld(worldName)
                .onFailure(failure -> Logging.severe("Failed to unload world %s: %s", worldName, failure))
                .onSuccess(success -> Logging.fine("Unloaded world %s as it was removed from config", worldName)));
    }

    /**
     * Load worlds that are already loaded by bukkit before Multiverse-Core is loaded.
     */
    private void loadDefaultWorlds() {
        Bukkit.getWorlds().forEach(bukkitWorld -> {
            if (isWorld(bukkitWorld.getName())) {
                return;
            }
            importWorld(ImportWorldOptions.worldName(bukkitWorld.getName())
                    .environment(bukkitWorld.getEnvironment())
                    .generator(generatorProvider.getDefaultGeneratorForWorld(bukkitWorld.getName())));
        });
    }

    /**
     * Loads all worlds that are set to autoload.
     */
    private void autoLoadWorlds() {
        getWorlds().forEach(world -> {
            if (isLoadedWorld(world) || !world.getAutoLoad()) {
                return;
            }
            loadWorld(world).onFailure(failure -> Logging.severe("Failed to load world %s: %s",
                    world.getName(), failure));
        });
    }

    @Override
    public Attempt<LoadedMultiverseWorld, CreateFailureReason> createWorld(CreateWorldOptions options) {
        return validateCreateWorldOptions(options).mapAttempt(this::createValidatedWorld);
    }

    private Attempt<CreateWorldOptions, CreateFailureReason> validateCreateWorldOptions(
            CreateWorldOptions options) {
        if (!worldNameChecker.isValidWorldName(options.worldName())) {
            return worldActionResult(CreateFailureReason.INVALID_WORLDNAME, options.worldName());
        } else if (getLoadedWorld(options.worldName()).isDefined()) {
            return worldActionResult(CreateFailureReason.WORLD_EXIST_LOADED, options.worldName());
        } else if (getWorld(options.worldName()).isDefined()) {
            return worldActionResult(CreateFailureReason.WORLD_EXIST_UNLOADED, options.worldName());
        } else if (worldNameChecker.hasWorldFolder(options.worldName())) {
            return worldActionResult(CreateFailureReason.WORLD_EXIST_FOLDER, options.worldName());
        }
        return worldActionResult(options);
    }

    private Attempt<LoadedMultiverseWorld, CreateFailureReason> createValidatedWorld(
            CreateWorldOptions options) {
        String parsedGenerator = parseGenerator(options.worldName(), options.generator());
        WorldCreator worldCreator = WorldCreator.name(options.worldName())
                .biomeProvider(createSingleBiomeProvider(options.biome()))
                .environment(options.environment())
                .generateStructures(options.generateStructures())
                .generator(parsedGenerator)
                .seed(options.seed())
                .type(options.worldType());
        return createBukkitWorld(worldCreator).fold(
                exception -> worldActionResult(CreateFailureReason.BUKKIT_CREATION_FAILED,
                        options.worldName(), exception),
                world -> {
                    LoadedMultiverseWorld loadedWorld = newLoadedMultiverseWorld(
                            world,
                            parsedGenerator,
                            options.useSpawnAdjust());
                    return worldActionResult(loadedWorld);
                });
    }

    @Override
    public Attempt<LoadedMultiverseWorld, ImportFailureReason> importWorld(
            ImportWorldOptions options) {
        return validateImportWorldOptions(options).mapAttempt(this::doImportWorld);
    }

    private Attempt<ImportWorldOptions, ImportFailureReason> validateImportWorldOptions(
            ImportWorldOptions options) {
        String worldName = options.worldName();
        if (!worldNameChecker.isValidWorldName(worldName)) {
            return worldActionResult(ImportFailureReason.INVALID_WORLDNAME, worldName);
        } else if (!worldNameChecker.isValidWorldFolder(worldName)) {
            return worldActionResult(ImportFailureReason.WORLD_FOLDER_INVALID, worldName);
        } else if (isLoadedWorld(worldName)) {
            return worldActionResult(ImportFailureReason.WORLD_EXIST_LOADED, worldName);
        } else if (isWorld(worldName)) {
            return worldActionResult(ImportFailureReason.WORLD_EXIST_UNLOADED, worldName);
        }
        return worldActionResult(options);
    }

    private Attempt<LoadedMultiverseWorld, ImportFailureReason> doImportWorld(
            ImportWorldOptions options) {
        String parsedGenerator = parseGenerator(options.worldName(), options.generator());
        WorldCreator worldCreator = WorldCreator.name(options.worldName())
                .biomeProvider(createSingleBiomeProvider(options.biome()))
                .environment(options.environment())
                .generator(parsedGenerator);
        return createBukkitWorld(worldCreator).fold(
                exception -> worldActionResult(ImportFailureReason.BUKKIT_CREATION_FAILED,
                        options.worldName(), exception),
                world -> {
                    LoadedMultiverseWorld loadedWorld = newLoadedMultiverseWorld(world,
                            parsedGenerator,
                            options.useSpawnAdjust());
                    return worldActionResult(loadedWorld);
                });
    }

    /**
     * Parses generator string and defaults to generator in bukkit.yml if not specified.
     *
     * @param worldName The name of the world.
     * @param generator The input generator string.
     * @return The parsed generator string.
     */
    private @Nullable String parseGenerator(@NotNull String worldName, @Nullable String generator) {
        return Strings.isNullOrEmpty(generator)
                ? generatorProvider.getDefaultGeneratorForWorld(worldName)
                : generator;
    }

    private SimpleMultiverseWorld newMultiverseWorld(String worldName, WorldConfig worldConfig) {
        SimpleMultiverseWorld mvWorld = new SimpleMultiverseWorld(worldName, worldConfig);
        worldsMap.put(mvWorld.getName(), mvWorld);
        corePermissions.addWorldPermissions(mvWorld);
        return mvWorld;
    }

    /**
     * Creates a new loaded multiverseWorld from a bukkit world.
     *
     * @param world         The bukkit world to create a multiverse world from.
     * @param generator     The generator string.
     * @param adjustSpawn   Whether to adjust spawn.
     */
    private SimpleLoadedMultiverseWorld newLoadedMultiverseWorld(
            @NotNull World world, @Nullable String generator, boolean adjustSpawn) {
        WorldConfig worldConfig = worldsConfigManager.addWorldConfig(world.getName());
        worldConfig.setAdjustSpawn(adjustSpawn);
        worldConfig.setGenerator(generator == null ? "" : generator);

        SimpleMultiverseWorld mvWorld = newMultiverseWorld(world.getName(), worldConfig);
        SimpleLoadedMultiverseWorld loadedWorld = new SimpleLoadedMultiverseWorld(
                world,
                worldConfig,
                blockSafety,
                locationManipulation);
        setDefaultEnvironmentScale(mvWorld);
        loadedWorldsMap.put(loadedWorld.getName(), loadedWorld);
        saveWorldsConfig();
        return loadedWorld;
    }

    private void setDefaultEnvironmentScale(MultiverseWorld world) {
        double scale = switch (world.getEnvironment()) {
            case NETHER -> 8.0;
            case THE_END -> 16.0;
            default -> 1.0;
        };
        world.setScale(scale);
    }

    @Override
    public Attempt<LoadedMultiverseWorld, LoadFailureReason> loadWorld(@NotNull String worldName) {
        return getWorld(worldName)
                .map(this::loadWorld)
                .getOrElse(() -> worldNameChecker.isValidWorldFolder(worldName)
                        ? worldActionResult(LoadFailureReason.WORLD_EXIST_FOLDER, worldName)
                        : worldActionResult(LoadFailureReason.WORLD_NON_EXISTENT, worldName));
    }

    @Override
    public Attempt<LoadedMultiverseWorld, LoadFailureReason> loadWorld(@NotNull MultiverseWorld world) {
        return validateWorldToLoad(world).mapAttempt(this::doLoadWorld);
    }

    private Attempt<MultiverseWorld, LoadFailureReason> validateWorldToLoad(
            @NotNull MultiverseWorld mvWorld) {
        if (loadTracker.contains(mvWorld.getName())) {
            // This is to prevent recursive calls by WorldLoadEvent
            Logging.fine("World already loading: " + mvWorld.getName());
            return worldActionResult(LoadFailureReason.WORLD_ALREADY_LOADING, mvWorld.getName());
        } else if (isLoadedWorld(mvWorld)) {
            Logging.severe("World already loaded: " + mvWorld.getName());
            return worldActionResult(LoadFailureReason.WORLD_EXIST_LOADED, mvWorld.getName());
        }
        return worldActionResult(mvWorld);
    }

    private Attempt<LoadedMultiverseWorld, LoadFailureReason> doLoadWorld(@NotNull MultiverseWorld mvWorld) {
        return createBukkitWorld(WorldCreator.name(mvWorld.getName())
                .biomeProvider(createSingleBiomeProvider(mvWorld.getBiome()))
                .environment(mvWorld.getEnvironment())
                .generator(Strings.isNullOrEmpty(mvWorld.getGenerator()) ? null : mvWorld.getGenerator())
                .seed(mvWorld.getSeed())).fold(
                        exception -> worldActionResult(LoadFailureReason.BUKKIT_CREATION_FAILED,
                                mvWorld.getName(), exception),
                        world -> {
                            WorldConfig worldConfig = worldsConfigManager.getWorldConfig(mvWorld.getName()).get();
                            SimpleLoadedMultiverseWorld loadedWorld = new SimpleLoadedMultiverseWorld(
                                    world,
                                    worldConfig,
                                    blockSafety,
                                    locationManipulation);
                            loadedWorldsMap.put(loadedWorld.getName(), loadedWorld);
                            saveWorldsConfig();
                            return worldActionResult(loadedWorld);
                        });
    }

    /**
     * Creates a single biome provider for the specified biome.
     * @param biome The biome
     * @return The single biome provider or null if biome is null or custom
     */
    private @Nullable BiomeProvider createSingleBiomeProvider(@Nullable Biome biome) {
        if (biome == null || biome == Biome.CUSTOM) {
            return null;
        }
        return new SingleBiomeProvider(biome);
    }

    @Override
    public Attempt<MultiverseWorld, UnloadFailureReason> unloadWorld(@NotNull UnloadWorldOptions options) {
        LoadedMultiverseWorld world = options.world();

        if (unloadTracker.contains(world.getName())) {
            // This is to prevent recursive calls by WorldUnloadEvent
            Logging.fine("World already unloading: " + world.getName());
            return worldActionResult(UnloadFailureReason.WORLD_ALREADY_UNLOADING, world.getName());
        }

        return unloadBukkitWorld(world.getBukkitWorld().getOrNull(), options.saveBukkitWorld()).fold(
                exception -> worldActionResult(UnloadFailureReason.BUKKIT_UNLOAD_FAILED,
                        world.getName(), exception),
                success -> Option.of(loadedWorldsMap.remove(world.getName())).fold(
                        () -> {
                            Logging.severe("Failed to remove world from map: " + world.getName());
                            return worldActionResult(UnloadFailureReason.WORLD_NON_EXISTENT, world.getName());
                        },
                        mvWorld -> {
                            Logging.fine("Removed MultiverseWorld from map: " + world.getName());
                            var unloadedWorld = Objects.requireNonNull(worldsMap.get(world.getName()),
                                    "For some reason, the unloaded world isn't in the map... BUGGG");
                            mvWorld.getWorldConfig().setMVWorld(unloadedWorld);
                            return worldActionResult(unloadedWorld);
                        }));
    }

    @Override
    public Attempt<String, RemoveFailureReason> removeWorld(
            @NotNull String worldName) {
        return getWorld(worldName)
                .map(this::removeWorld)
                .getOrElse(() -> worldActionResult(RemoveFailureReason.WORLD_NON_EXISTENT, worldName));
    }

    @Override
    public Attempt<String, RemoveFailureReason> removeWorld(@NotNull MultiverseWorld world) {
        return getLoadedWorld(world).fold(
                () -> removeWorldFromConfig(world),
                this::removeWorld);
    }

    @Override
    public Attempt<String, RemoveFailureReason> removeWorld(@NotNull LoadedMultiverseWorld loadedWorld) {
        return unloadWorld(UnloadWorldOptions.world(loadedWorld))
                .transform(RemoveFailureReason.UNLOAD_FAILED)
                .mapAttempt(this::removeWorldFromConfig);
    }

    /**
     * Removes an existing multiverse world from the world's config. It will no longer be a world known to Multiverse.
     *
     * @param world The multiverse world to remove.
     * @return The result of the remove.
     */
    private Attempt<String, RemoveFailureReason> removeWorldFromConfig(@NotNull MultiverseWorld world) {
        // Remove world from config
        worldsMap.remove(world.getName());
        ((SimpleMultiverseWorld) world).getWorldConfig().deferenceMVWorld();
        worldsConfigManager.deleteWorldConfig(world.getName());
        saveWorldsConfig();
        corePermissions.removeWorldPermissions(world);
        return worldActionResult(world.getName());
    }

    @Override
    public Attempt<String, DeleteFailureReason> deleteWorld(@NotNull String worldName) {
        return getWorld(worldName)
                .map(this::deleteWorld)
                .getOrElse(() -> worldActionResult(DeleteFailureReason.WORLD_NON_EXISTENT, worldName));
    }

    @Override
    public Attempt<String, DeleteFailureReason> deleteWorld(@NotNull MultiverseWorld world) {
        return getLoadedWorld(world).fold(
                () -> loadWorld(world)
                        .transform(DeleteFailureReason.LOAD_FAILED)
                        .mapAttempt(this::deleteWorld),
                this::deleteWorld);
    }

    @Override
    public Attempt<String, DeleteFailureReason> deleteWorld(@NotNull LoadedMultiverseWorld world) {
        AtomicReference<File> worldFolder = new AtomicReference<>();
        return validateWorldToDelete(world)
                .peek(worldFolder::set)
                .mapAttempt(() -> {
                    MVWorldDeleteEvent event = new MVWorldDeleteEvent(world);
                    pluginManager.callEvent(event);
                    return event.isCancelled()
                            ? Attempt.failure(DeleteFailureReason.EVENT_CANCELLED)
                            : Attempt.success(null);
                })
                .mapAttempt(() -> removeWorld(world).transform(DeleteFailureReason.REMOVE_FAILED))
                .mapAttempt(() -> filesManipulator.deleteFolder(worldFolder.get()).fold(
                        exception -> worldActionResult(DeleteFailureReason.FAILED_TO_DELETE_FOLDER,
                                world.getName(), exception),
                        success -> worldActionResult(world.getName())));
    }

    private Attempt<File, DeleteFailureReason> validateWorldToDelete(
            @NotNull LoadedMultiverseWorld world) {
        File worldFolder = world.getBukkitWorld().map(World::getWorldFolder).getOrNull();
        if (worldFolder == null || !worldNameChecker.isValidWorldFolder(worldFolder)) {
            Logging.severe("Failed to get world folder for world: " + world.getName());
            return worldActionResult(DeleteFailureReason.WORLD_FOLDER_NOT_FOUND, world.getName());
        }
        return worldActionResult(worldFolder);
    }

    @Override
    public Attempt<LoadedMultiverseWorld, CloneFailureReason> cloneWorld(@NotNull CloneWorldOptions options) {
        return cloneWorldValidateWorld(options)
                .mapAttempt(this::cloneWorldCopyFolder)
                .mapAttempt(validatedOptions -> {
                    ImportWorldOptions importWorldOptions = ImportWorldOptions
                            .worldName(validatedOptions.newWorldName())
                            .biome(validatedOptions.world().getBiome())
                            .environment(validatedOptions.world().getEnvironment())
                            .generator(validatedOptions.world().getGenerator());
                    return importWorld(importWorldOptions).transform(CloneFailureReason.IMPORT_FAILED);
                })
                .onSuccess(newWorld -> {
                    cloneWorldTransferData(options, newWorld);
                    if (options.keepWorldConfig()) {
                        newWorld.setSpawnLocation(options.world().getSpawnLocation());
                    }
                    saveWorldsConfig();
                });
    }

    private Attempt<CloneWorldOptions, CloneFailureReason> cloneWorldValidateWorld(
            @NotNull CloneWorldOptions options) {
        String newWorldName = options.newWorldName();
        if (!worldNameChecker.isValidWorldName(newWorldName)) {
            Logging.severe("Invalid world name: " + newWorldName);
            return worldActionResult(CloneFailureReason.INVALID_WORLDNAME, newWorldName);
        }
        if (isLoadedWorld(newWorldName)) {
            Logging.severe("World already loaded when attempting to clone: " + newWorldName);
            return worldActionResult(CloneFailureReason.WORLD_EXIST_LOADED, newWorldName);
        }
        if (isWorld(newWorldName)) {
            Logging.severe("World already exist unloaded: " + newWorldName);
            return worldActionResult(CloneFailureReason.WORLD_EXIST_UNLOADED, newWorldName);
        }
        if (worldNameChecker.hasWorldFolder(newWorldName)) {
            return worldActionResult(CloneFailureReason.WORLD_EXIST_FOLDER, newWorldName);
        }
        return worldActionResult(options);
    }

    private Attempt<CloneWorldOptions, CloneFailureReason> cloneWorldCopyFolder(
            @NotNull CloneWorldOptions options) {
        File worldFolder = options.world().getBukkitWorld().map(World::getWorldFolder).get();
        File newWorldFolder = new File(Bukkit.getWorldContainer(), options.newWorldName());
        return filesManipulator.copyFolder(worldFolder, newWorldFolder, CLONE_IGNORE_FILES).fold(
                exception -> worldActionResult(CloneFailureReason.COPY_FAILED,
                        options.world().getName(), exception),
                success -> worldActionResult(options));
    }

    private void cloneWorldTransferData(@NotNull CloneWorldOptions options, @NotNull LoadedMultiverseWorld newWorld) {
        DataTransfer<LoadedMultiverseWorld> dataTransfer = transferData(options, options.world());
        dataTransfer.pasteAllTo(newWorld);
    }

    private DataTransfer<LoadedMultiverseWorld> transferData(
            @NotNull KeepWorldSettingsOptions options, @NotNull LoadedMultiverseWorld world) {
        DataTransfer<LoadedMultiverseWorld> dataTransfer = new DataTransfer<>();

        if (options.keepWorldConfig()) {
            dataTransfer.addDataStore(new WorldConfigStore(), world);
        }
        if (options.keepGameRule()) {
            dataTransfer.addDataStore(new GameRulesStore(), world);
        }
        if (options.keepWorldBorder()) {
            dataTransfer.addDataStore(new WorldBorderStore(), world);
        }

        return dataTransfer;
    }

    @Override
    public Attempt<LoadedMultiverseWorld, RegenFailureReason> regenWorld(@NotNull RegenWorldOptions options) {
        LoadedMultiverseWorld world = options.world();
        DataTransfer<LoadedMultiverseWorld> dataTransfer = transferData(options, world);
        boolean shouldKeepSpawnLocation = options.keepWorldConfig() && options.seed() == world.getSeed();
        Location spawnLocation = world.getSpawnLocation();

        CreateWorldOptions createWorldOptions = CreateWorldOptions.worldName(world.getName())
                .environment(world.getEnvironment())
                .generateStructures(world.canGenerateStructures().getOrElse(true))
                .generator(world.getGenerator())
                .seed(options.seed())
                .useSpawnAdjust(!shouldKeepSpawnLocation && world.getAdjustSpawn())
                .worldType(world.getWorldType().getOrElse(WorldType.NORMAL));

        return deleteWorld(world)
                .transform(RegenFailureReason.DELETE_FAILED)
                .mapAttempt(() -> createWorld(createWorldOptions).transform(RegenFailureReason.CREATE_FAILED))
                .onSuccess(newWorld -> {
                    dataTransfer.pasteAllTo(newWorld);
                    if (shouldKeepSpawnLocation) {
                        // Special case for spawn location to prevent unsafe location if world was regen using a
                        // different seed.
                        newWorld.setSpawnLocation(spawnLocation);
                    }
                    saveWorldsConfig();
                });
    }

    private <T, F extends FailureReason> Attempt<T, F> worldActionResult(@NotNull T value) {
        return Attempt.success(value);
    }

    private <T, F extends FailureReason> Attempt<T, F> worldActionResult(
            @NotNull F failureReason, @NotNull String worldName) {
        return Attempt.failure(failureReason, replaceWorldName(worldName));
    }

    private <T, F extends FailureReason> Attempt<T, F> worldActionResult(
            @NotNull F failureReason, @NotNull String worldName, @NotNull Throwable error) {
        // TODO: Localize error message if its a MultiverseException
        return Attempt.failure(failureReason, replaceWorldName(worldName), replaceError(error.getMessage()));
    }

    private MessageReplacement replaceWorldName(@NotNull String worldName) {
        return replace("{world}").with(worldName);
    }

    private MessageReplacement replaceError(@NotNull String errorMessage) {
        return replace("{error}").with(errorMessage);
    }

    /**
     * Creates a bukkit world.
     *
     * @param worldCreator  The world parameters.
     * @return The created world.
     */
    private Try<World> createBukkitWorld(WorldCreator worldCreator) {
        return Try.of(() -> {
            this.loadTracker.add(worldCreator.name());
            World world = worldCreator.createWorld();
            if (world == null) {
                // TODO: Localize this
                throw new Exception("World created returned null!");
            }
            Logging.fine("Bukkit created world: " + world.getName());
            return world;
        }).onFailure(exception -> {
            Logging.severe("Failed to create bukkit world: " + worldCreator.name());
            exception.printStackTrace();
        }).andFinally(() -> this.loadTracker.remove(worldCreator.name()));
    }

    /**
     * Unloads a bukkit world.
     *
     * @param world The bukkit world to unload.
     * @return The unloaded world.
     */
    private Try<Void> unloadBukkitWorld(World world, boolean save) {
        return Try.run(() -> {
            if (world == null) {
                return;
            }
            unloadTracker.add(world.getName());
            if (!Bukkit.unloadWorld(world, save)) {
                // TODO: Localize this, maybe with MultiverseException
                if (!world.getPlayers().isEmpty()) {
                    throw new Exception("There are still players in the world! Please use --remove-players flag to "
                            + "your command if wish to teleport all players out of the world.");
                }
                throw new Exception("Is this the default world? You can't unload the default world!");
            }
            Logging.fine("Bukkit unloaded world: " + world.getName());
        }).andFinally(() -> unloadTracker.remove(world.getName()));
    }

    @Override
    public List<String> getPotentialWorlds() {
        File[] files = Bukkit.getWorldContainer().listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(files)
                .filter(file -> !isWorld(file.getName()))
                .filter(worldNameChecker::isValidWorldFolder)
                .map(File::getName)
                .toList();
    }

    @Override
    public Option<MultiverseWorld> getWorld(@Nullable World world) {
        return Option.of(world).map(World::getName).flatMap(this::getWorld);
    }

    @Override
    public Option<MultiverseWorld> getWorld(@Nullable String worldName) {
        return getLoadedWorld(worldName).fold(() -> getUnloadedWorld(worldName), Option::of);
    }

    @Override
    public Option<MultiverseWorld> getWorldByNameOrAlias(@Nullable String worldNameOrAlias) {
        return getLoadedWorldByNameOrAlias(worldNameOrAlias)
                .fold(() -> getUnloadedWorldByNameOrAlias(worldNameOrAlias), Option::of);
    }

    @Override
    public Collection<MultiverseWorld> getWorlds() {
        return worldsMap.values().stream()
                .map(world -> getLoadedWorld(world)
                        .fold(() -> (MultiverseWorld) world, loadedWorld -> loadedWorld))
                .toList();
    }

    @Override
    public boolean isWorld(@Nullable String worldName) {
        return worldsMap.containsKey(worldName);
    }

    @Override
    public Option<MultiverseWorld> getUnloadedWorld(@Nullable String worldName) {
        return isLoadedWorld(worldName) ? Option.none() : Option.of(worldsMap.get(worldName));
    }

    @Override
    public Option<MultiverseWorld> getUnloadedWorldByNameOrAlias(@Nullable String worldNameOrAlias) {
        return getUnloadedWorld(worldNameOrAlias)
                .orElse(() -> Option.ofOptional(worldsMap.values().stream()
                        .filter(world -> !world.isLoaded())
                        .filter(world -> world.getAlias().equalsIgnoreCase(worldNameOrAlias))
                        .findFirst()));
    }

    @Override
    public Collection<MultiverseWorld> getUnloadedWorlds() {
        return worldsMap.values().stream()
                .filter(world -> !world.isLoaded())
                .map(world -> (MultiverseWorld) world)
                .toList();
    }

    @Override
    public boolean isUnloadedWorld(@Nullable String worldName) {
        return !isLoadedWorld(worldName) && isWorld(worldName);
    }

    @Override
    public Option<LoadedMultiverseWorld> getLoadedWorld(@Nullable World world) {
        return world == null ? Option.none() : Option.of((LoadedMultiverseWorld) loadedWorldsMap.get(world.getName()));
    }

    @Override
    public Option<LoadedMultiverseWorld> getLoadedWorld(@Nullable MultiverseWorld world) {
        return world == null ? Option.none() : Option.of((LoadedMultiverseWorld) loadedWorldsMap.get(world.getName()));
    }

    @Override
    public Option<LoadedMultiverseWorld> getLoadedWorld(@Nullable String worldName) {
        return Option.of((LoadedMultiverseWorld) loadedWorldsMap.get(worldName));
    }

    @Override
    public Option<LoadedMultiverseWorld> getLoadedWorldByNameOrAlias(@Nullable String worldNameOrAlias) {
        return getLoadedWorld(worldNameOrAlias)
                .orElse(() -> Option.ofOptional(loadedWorldsMap.values().stream()
                        .filter(world -> world.getAlias().equalsIgnoreCase(worldNameOrAlias))
                        .map(world -> (LoadedMultiverseWorld) world)
                        .findFirst()));
    }

    @Override
    public Collection<LoadedMultiverseWorld> getLoadedWorlds() {
        return loadedWorldsMap.values().stream()
                .map(world -> (LoadedMultiverseWorld) world)
                .toList();
    }

    @Override
    public boolean isLoadedWorld(@Nullable World world) {
        return world != null && isLoadedWorld(world.getName());
    }

    @Override
    public boolean isLoadedWorld(@Nullable MultiverseWorld world) {
        return world != null && isLoadedWorld(world.getName());
    }

    @Override
    public boolean isLoadedWorld(@Nullable String worldName) {
        return loadedWorldsMap.containsKey(worldName);
    }

    @Override
    public boolean saveWorldsConfig() {
        return worldsConfigManager.save()
                .onFailure(failure -> {
                    Logging.severe("Failed to save worlds config: %s", failure);
                    failure.printStackTrace();
                })
                .isSuccess();
    }
}
