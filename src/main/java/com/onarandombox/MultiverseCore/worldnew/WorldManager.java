package com.onarandombox.MultiverseCore.worldnew;

import com.dumptruckman.minecraft.util.Logging;
import com.google.common.base.Strings;
import com.onarandombox.MultiverseCore.api.BlockSafety;
import com.onarandombox.MultiverseCore.api.LocationManipulation;
import com.onarandombox.MultiverseCore.api.SafeTTeleporter;
import com.onarandombox.MultiverseCore.utils.result.Result;
import com.onarandombox.MultiverseCore.worldnew.config.WorldConfig;
import com.onarandombox.MultiverseCore.worldnew.config.WorldsConfigManager;
import com.onarandombox.MultiverseCore.worldnew.generators.GeneratorProvider;
import com.onarandombox.MultiverseCore.worldnew.helpers.DataStore.GameRulesStore;
import com.onarandombox.MultiverseCore.worldnew.helpers.DataTransfer;
import com.onarandombox.MultiverseCore.worldnew.helpers.FilesManipulator;
import com.onarandombox.MultiverseCore.worldnew.options.CloneWorldOptions;
import com.onarandombox.MultiverseCore.worldnew.options.CreateWorldOptions;
import com.onarandombox.MultiverseCore.worldnew.options.ImportWorldOptions;
import com.onarandombox.MultiverseCore.worldnew.options.RegenWorldOptions;
import com.onarandombox.MultiverseCore.worldnew.results.CloneWorldResult;
import com.onarandombox.MultiverseCore.worldnew.results.CreateWorldResult;
import com.onarandombox.MultiverseCore.worldnew.results.DeleteWorldResult;
import com.onarandombox.MultiverseCore.worldnew.results.ImportWorldResult;
import com.onarandombox.MultiverseCore.worldnew.results.LoadWorldResult;
import com.onarandombox.MultiverseCore.worldnew.results.RegenWorldResult;
import com.onarandombox.MultiverseCore.worldnew.results.RemoveWorldResult;
import com.onarandombox.MultiverseCore.worldnew.results.UnloadWorldResult;
import io.vavr.control.Option;
import io.vavr.control.Try;
import jakarta.inject.Inject;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jvnet.hk2.annotations.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.onarandombox.MultiverseCore.utils.message.MessageReplacement.replace;
import static com.onarandombox.MultiverseCore.worldnew.helpers.DataStore.WorldBorderStore;
import static com.onarandombox.MultiverseCore.worldnew.helpers.DataStore.WorldConfigStore;

/**
 * This manager contains all the world managing functions that your heart desires!
 */
@Service
public class WorldManager {
    private static final List<String> CLONE_IGNORE_FILES = Arrays.asList("uid.dat", "session.lock");

    private final Map<String, MultiverseWorld> worldsMap;
    private final Map<String, LoadedMultiverseWorld> loadedWorldsMap;
    private final List<String> unloadTracker;
    private final List<String> loadTracker;
    private final WorldsConfigManager worldsConfigManager;
    private final WorldNameChecker worldNameChecker;
    private final GeneratorProvider generatorProvider;
    private final FilesManipulator filesManipulator;
    private final BlockSafety blockSafety;
    private final SafeTTeleporter safeTTeleporter;
    private final LocationManipulation locationManipulation;

    @Inject
    WorldManager(
            @NotNull WorldsConfigManager worldsConfigManager,
            @NotNull WorldNameChecker worldNameChecker,
            @NotNull GeneratorProvider generatorProvider,
            @NotNull FilesManipulator filesManipulator,
            @NotNull BlockSafety blockSafety,
            @NotNull SafeTTeleporter safeTTeleporter,
            @NotNull LocationManipulation locationManipulation
    ) {
        this.worldsMap = new HashMap<>();
        this.loadedWorldsMap = new HashMap<>();
        this.unloadTracker = new ArrayList<>();
        this.loadTracker = new ArrayList<>();

        this.worldsConfigManager = worldsConfigManager;
        this.worldNameChecker = worldNameChecker;
        this.generatorProvider = generatorProvider;
        this.filesManipulator = filesManipulator;
        this.blockSafety = blockSafety;
        this.safeTTeleporter = safeTTeleporter;
        this.locationManipulation = locationManipulation;
    }

    /**
     * Loads all worlds from the worlds config.
     */
    public void initAllWorlds() {
        if (!populateWorldFromConfig()) {
            return;
        }
        loadDefaultWorlds();
        autoLoadWorlds();
        saveWorldsConfig();
    }

    /**
     * Generate worlds from the worlds config.
     */
    private boolean populateWorldFromConfig() {
        Try<Void> load = worldsConfigManager.load();
        if (load.isFailure()) {
            Logging.severe("Failed to load worlds config: " + load.getCause().getMessage());
            load.getCause().printStackTrace();
            return false;
        }
        worldsConfigManager.getAllWorldConfigs().forEach(worldConfig -> {
            getLoadedWorld(worldConfig.getWorldName())
                    .peek(loadedWorld -> loadedWorld.setWorldConfig(worldConfig));
            getWorld(worldConfig.getWorldName())
                    .peek(unloadedWorld -> unloadedWorld.setWorldConfig(worldConfig))
                    .onEmpty(() -> {
                        MultiverseWorld mvWorld = new MultiverseWorld(worldConfig.getWorldName(), worldConfig);
                        worldsMap.put(mvWorld.getName(), mvWorld);
                    });
        });
        return true;
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
            loadWorld(world).onFailure(failure -> Logging.severe("Failed to load world %s: %s", world.getName(), failure));
        });
    }

    /**
     * Creates a new world.
     *
     * @param options   The options for customizing the creation of a new world.
     */
    public Result<CreateWorldResult.Success, CreateWorldResult.Failure> createWorld(CreateWorldOptions options) {
        // Params validations
        if (!worldNameChecker.isValidWorldName(options.worldName())) {
            return Result.failure(CreateWorldResult.Failure.INVALID_WORLDNAME,
                    replace("{world}").with(options.worldName()));
        }
        if (getLoadedWorld(options.worldName()).isDefined()) {
            return Result.failure(CreateWorldResult.Failure.WORLD_EXIST_LOADED,
                    replace("{world}").with(options.worldName()));
        }
        if (getWorld(options.worldName()).isDefined()) {
            return Result.failure(CreateWorldResult.Failure.WORLD_EXIST_UNLOADED,
                    replace("{world}").with(options.worldName()));
        }
        File worldFolder = new File(Bukkit.getWorldContainer(), options.worldName());
        if (worldFolder.exists()) {
            return Result.failure(CreateWorldResult.Failure.WORLD_EXIST_FOLDER,
                    replace("{world}").with(options.worldName()));
        }

        String parsedGenerator = parseGenerator(options.worldName(), options.generator());
        return createBukkitWorld(WorldCreator.name(options.worldName())
                .environment(options.environment())
                .generateStructures(options.generateStructures())
                .generator(parsedGenerator)
                .seed(options.seed())
                .type(options.worldType()))
                .fold(
                        exception -> Result.failure(CreateWorldResult.Failure.BUKKIT_CREATION_FAILED,
                                replace("{world}").with(options.worldName()),
                                replace("{error}").with(exception.getMessage())),
                        world -> {
                            newMVWorld(world, parsedGenerator, options.useSpawnAdjust());
                            return Result.success(CreateWorldResult.Success.CREATED,
                                    replace("{world}").with(world.getName()));
                        });
    }

    /**
     * Imports an existing world folder.
     *
     * @param options   The options for customizing the import of an existing world folder.
     * @return The result of the import.
     */
    public Result<ImportWorldResult.Success, ImportWorldResult.Failure> importWorld(ImportWorldOptions options) {
        // Params validations
        if (!worldNameChecker.isValidWorldName(options.worldName())) {
            return Result.failure(ImportWorldResult.Failure.INVALID_WORLDNAME,
                    replace("{world}").with(options.worldName()));
        }
        if (!worldNameChecker.isValidWorldFolder(options.worldName())) {
            return Result.failure(ImportWorldResult.Failure.WORLD_FOLDER_INVALID,
                    replace("{world}").with(options.worldName()));
        }
        if (isLoadedWorld(options.worldName())) {
            return Result.failure(ImportWorldResult.Failure.WORLD_EXIST_LOADED,
                    replace("{world}").with(options.worldName()));
        }
        if (isWorld(options.worldName())) {
            return Result.failure(ImportWorldResult.Failure.WORLD_EXIST_UNLOADED,
                    replace("{world}").with(options.worldName()));
        }

        String parsedGenerator = parseGenerator(options.worldName(), options.generator());
        return createBukkitWorld(WorldCreator.name(options.worldName())
                .environment(options.environment())
                .generator(parsedGenerator))
                .fold(
                        exception -> Result.failure(ImportWorldResult.Failure.BUKKIT_CREATION_FAILED,
                                replace("{world}").with(options.worldName()),
                                replace("{error}").with(exception.getMessage())),
                        world -> {
                            newMVWorld(world, parsedGenerator, options.useSpawnAdjust());
                            return Result.success(ImportWorldResult.Success.IMPORTED,
                                    replace("{world}").with(options.worldName()));
                        });
    }

    private @Nullable String parseGenerator(@NotNull String worldName, @Nullable String generator) {
        return Strings.isNullOrEmpty(generator)
                ? generatorProvider.getDefaultGeneratorForWorld(worldName)
                : generator;
    }

    private void newMVWorld(@NotNull World world, @Nullable String generator, boolean adjustSpawn) {
        WorldConfig worldConfig = worldsConfigManager.addWorldConfig(world.getName());
        worldConfig.setAdjustSpawn(adjustSpawn);
        worldConfig.setGenerator(generator == null ? "" : generator);

        MultiverseWorld mvWorld = new MultiverseWorld(world.getName(), worldConfig);
        worldsMap.put(mvWorld.getName(), mvWorld);

        LoadedMultiverseWorld loadedWorld = new LoadedMultiverseWorld(world, worldConfig, blockSafety, safeTTeleporter, locationManipulation);
        loadedWorldsMap.put(loadedWorld.getName(), loadedWorld);
        saveWorldsConfig();
    }

    /**
     * Loads an existing world in config.
     *
     * @param worldName The name of the world to load.
     * @return The result of the load.
     */
    public Result<LoadWorldResult.Success, LoadWorldResult.Failure> loadWorld(@NotNull String worldName) {
        return getWorld(worldName)
                .map(this::loadWorld)
                .getOrElse(() -> worldNameChecker.isValidWorldFolder(worldName)
                        ? Result.failure(LoadWorldResult.Failure.WORLD_EXIST_FOLDER,
                                replace("{world}").with(worldName))
                        : Result.failure(LoadWorldResult.Failure.WORLD_NON_EXISTENT,
                                replace("{world}").with(worldName)));
    }

    /**
     * Loads an existing world in config.
     *
     * @param mvWorld  The world to load.
     * @return The result of the load.
     */
    public Result<LoadWorldResult.Success, LoadWorldResult.Failure> loadWorld(@NotNull MultiverseWorld mvWorld) {
        // Params validations
        if (loadTracker.contains(mvWorld.getName())) {
            // This is to prevent recursive calls by WorldLoadEvent
            Logging.fine("World already loading: " + mvWorld.getName());
            return Result.failure(LoadWorldResult.Failure.WORLD_ALREADY_LOADING,
                    replace("{world}").with(mvWorld.getName()));
        }
        if (isLoadedWorld(mvWorld)) {
            Logging.severe("World already loaded: " + mvWorld.getName());
            return Result.failure(LoadWorldResult.Failure.WORLD_EXIST_LOADED,
                    replace("{world}").with(mvWorld.getName()));
        }

        return createBukkitWorld(WorldCreator.name(mvWorld.getName())
                .environment(mvWorld.getEnvironment())
                .generator(Strings.isNullOrEmpty(mvWorld.getGenerator()) ? null : mvWorld.getGenerator())
                .seed(mvWorld.getSeed())).fold(
                        exception -> Result.failure(LoadWorldResult.Failure.BUKKIT_CREATION_FAILED,
                                replace("{world}").with(mvWorld.getName()),
                                replace("{error}").with(exception.getMessage())),
                        world -> {
                            WorldConfig worldConfig = worldsConfigManager.getWorldConfig(mvWorld.getName());
                            LoadedMultiverseWorld loadedWorld = new LoadedMultiverseWorld(world, worldConfig, blockSafety,
                                    safeTTeleporter, locationManipulation);
                            loadedWorldsMap.put(loadedWorld.getName(), loadedWorld);
                            saveWorldsConfig();
                            return Result.success(LoadWorldResult.Success.LOADED,
                                    replace("{world}").with(loadedWorld.getName()));
                        });
    }

    /**
     * Unloads an existing multiverse world. It will still remain as an unloaded world in mv config.
     *
     * @param world The bukkit world to unload.
     * @return The result of the unload action.
     */
    public Result<UnloadWorldResult.Success, UnloadWorldResult.Failure> unloadWorld(@NotNull World world) {
        return unloadWorld(world.getName());
    }

    /**
     * Unloads an existing multiverse world. It will still remain as an unloaded world in mv config.
     *
     * @param worldName The name of the world to unload.
     * @return The result of the unload action.
     */
    public Result<UnloadWorldResult.Success, UnloadWorldResult.Failure> unloadWorld(@NotNull String worldName) {
        return getLoadedWorld(worldName)
                .map(this::unloadWorld)
                .getOrElse(() -> isUnloadedWorld(worldName)
                        ? Result.failure(UnloadWorldResult.Failure.WORLD_UNLOADED,
                                replace("{world}").with(worldName))
                        : Result.failure(UnloadWorldResult.Failure.WORLD_NON_EXISTENT,
                                replace("{world}").with(worldName)));
    }

    /**
     * Unloads an existing multiverse world. It will still remain as an unloaded world.
     *
     * @param world The multiverse world to unload.
     * @return The result of the unload action.
     */
    public Result<UnloadWorldResult.Success, UnloadWorldResult.Failure> unloadWorld(@NotNull LoadedMultiverseWorld world) {
        if (unloadTracker.contains(world.getName())) {
            // This is to prevent recursive calls by WorldUnloadEvent
            Logging.fine("World already unloading: " + world.getName());
            return Result.failure(UnloadWorldResult.Failure.WORLD_ALREADY_UNLOADING,
                    replace("{world}").with(world.getName()));
        }

        // TODO: removePlayersFromWorld?

        return unloadBukkitWorld(world.getBukkitWorld().getOrNull()).fold(
                exception -> Result.failure(UnloadWorldResult.Failure.BUKKIT_UNLOAD_FAILED,
                        replace("{world}").with(world.getName()),
                        replace("{error}").with(exception.getMessage())),
                success -> Option.of(loadedWorldsMap.remove(world.getName())).fold(
                        () -> {
                            Logging.severe("Failed to remove world from map: " + world.getName());
                            return Result.failure(UnloadWorldResult.Failure.WORLD_NON_EXISTENT,
                                    replace("{world}").with(world.getName()));
                        },
                        mvWorld -> {
                            Logging.fine("Removed MVWorld from map: " + world.getName());
                            mvWorld.getWorldConfig().deferenceMVWorld();
                            return Result.success(UnloadWorldResult.Success.UNLOADED,
                                    replace("{world}").with(world.getName()));
                        }));
    }

    /**
     * Removes an existing multiverse world. It will be deleted from the worlds config and will no longer be an
     * unloaded world. World files will not be deleted.
     *
     * @param worldName The name of the world to remove.
     * @return The result of the remove.
     */
    public Result<RemoveWorldResult.Success, RemoveWorldResult.Failure> removeWorld(@NotNull String worldName) {
        return getWorld(worldName)
                .map(this::removeWorld)
                .getOrElse(() -> Result.failure(RemoveWorldResult.Failure.WORLD_NON_EXISTENT, replace("{world}").with(worldName)));
    }

    /**
     * Removes an existing multiverse world. It will be deleted from the worlds config and will no longer be an
     * unloaded world. World files will not be deleted.
     *
     * @param world The multiverse world to remove.
     * @return The result of the remove.
     */
    public Result<RemoveWorldResult.Success, RemoveWorldResult.Failure> removeWorld(@NotNull MultiverseWorld world) {
        return getLoadedWorld(world).fold(
                () -> removeWorldFromConfig(world),
                this::removeWorld);
    }

    /**
     * Removes an existing multiverse world. It will be deleted from the worlds config and will no longer be an
     * unloaded world. World files will not be deleted.
     *
     * @param loadedWorld The multiverse world to remove.
     * @return The result of the remove.
     */
    public Result<RemoveWorldResult.Success, RemoveWorldResult.Failure> removeWorld(@NotNull LoadedMultiverseWorld loadedWorld) {
        var result = unloadWorld(loadedWorld);
        if (result.isFailure()) {
            return Result.failure(RemoveWorldResult.Failure.UNLOAD_FAILED, result.getReasonMessage());
        }
        return removeWorldFromConfig(loadedWorld);
    }

    /**
     * Removes an existing multiverse world from the world's config. It will no longer be an world known to Multiverse.
     *
     * @param world The multiverse world to remove.
     * @return The result of the remove.
     */
    private Result<RemoveWorldResult.Success, RemoveWorldResult.Failure>
            removeWorldFromConfig(@NotNull MultiverseWorld world) {
        // Remove world from config
        worldsMap.remove(world.getName());
        worldsConfigManager.deleteWorldConfig(world.getName());
        saveWorldsConfig();

        return Result.success(RemoveWorldResult.Success.REMOVED);
    }

    /**
     * Deletes an existing multiverse world entirely. World will be loaded if it is not already loaded.
     * Warning: This will delete all world files.
     *
     * @param worldName The name of the world to delete.
     * @return The result of the delete action.
     */
    public Result<DeleteWorldResult.Success, DeleteWorldResult.Failure> deleteWorld(@NotNull String worldName) {
        return getWorld(worldName)
                .map(this::deleteWorld)
                .getOrElse(() -> Result.failure(DeleteWorldResult.Failure.WORLD_NON_EXISTENT,
                        replace("{world}").with(worldName)));
    }

    /**
     * Deletes an existing multiverse world entirely. World will be loaded if it is not already loaded.
     * Warning: This will delete all world files.
     *
     * @param world The world to delete.
     * @return The result of the delete action.
     */
    public Result<DeleteWorldResult.Success, DeleteWorldResult.Failure> deleteWorld(@NotNull MultiverseWorld world) {
        return getLoadedWorld(world).fold(
                () -> {
                    var result = loadWorld(world);
                    if (result.isFailure()) {
                        return Result.failure(DeleteWorldResult.Failure.LOAD_FAILED,
                                replace("{world}").with(world.getName()));
                    }
                    return deleteWorld(world);
                },
                this::deleteWorld);
    }

    /**
     * Deletes an existing multiverse world entirely. Warning: This will delete all world files.
     *
     * @param world The multiverse world to delete.
     * @return The result of the delete action.
     */
    public Result<DeleteWorldResult.Success, DeleteWorldResult.Failure> deleteWorld(@NotNull LoadedMultiverseWorld world) {
        File worldFolder = world.getBukkitWorld().map(World::getWorldFolder).getOrNull();
        if (worldFolder == null || !worldNameChecker.isValidWorldFolder(worldFolder)) {
            Logging.severe("Failed to get world folder for world: " + world.getName());
            return Result.failure(DeleteWorldResult.Failure.WORLD_FOLDER_NOT_FOUND,
                    replace("{world}").with(world.getName()));
        }

        var result = removeWorld(world);
        if (result.isFailure()) {
            return Result.failure(DeleteWorldResult.Failure.REMOVE_FAILED, result.getReasonMessage());
        }

        // Erase world files from disk
        // TODO: Possible config options to keep certain files
        return filesManipulator.deleteFolder(worldFolder).fold(
                exception -> Result.failure(DeleteWorldResult.Failure.FAILED_TO_DELETE_FOLDER,
                        replace("{world}").with(world.getName()),
                        replace("{error}").with(exception.getMessage())),
                success -> Result.success(DeleteWorldResult.Success.DELETED,
                        replace("{world}").with(world.getName())));
    }

    /**
     * Clones an existing multiverse world.
     *
     * @param options   The options for customizing the cloning of a world.
     */
    public Result<CloneWorldResult.Success, CloneWorldResult.Failure> cloneWorld(@NotNull CloneWorldOptions options) {
        return cloneWorldValidateWorld(options)
                .onSuccessThen(s -> cloneWorldCopyFolder(options))
                .onSuccessThen(s -> importWorld(
                        ImportWorldOptions.worldName(options.newWorldName())
                                .environment(options.world().getEnvironment())
                                .generator(options.world().getGenerator()))
                        .fold(
                                failure -> Result.failure(CloneWorldResult.Failure.IMPORT_FAILED, failure.getReasonMessage()),
                                success -> Result.success()))
                .onSuccessThen(s -> getLoadedWorld(options.newWorldName()).fold(
                        () -> Result.failure(CloneWorldResult.Failure.MV_WORLD_FAILED,
                                replace("{world}").with(options.newWorldName())),
                        mvWorld -> {
                            cloneWorldTransferData(options, mvWorld);
                            saveWorldsConfig();
                            return Result.success(CloneWorldResult.Success.CLONED,
                                    replace("{world}").with(options.world().getName()),
                                    replace("{newworld}").with(mvWorld.getName()));
                        }));
    }

    private Result<CloneWorldResult.Success, CloneWorldResult.Failure>
            cloneWorldValidateWorld(@NotNull CloneWorldOptions options) {
        String newWorldName = options.newWorldName();
        if (!worldNameChecker.isValidWorldName(newWorldName)) {
            Logging.severe("Invalid world name: " + newWorldName);
            return Result.failure(CloneWorldResult.Failure.INVALID_WORLDNAME, replace("{world}").with(newWorldName));
        }
        if (worldNameChecker.isValidWorldFolder(newWorldName)) {
            return Result.failure(CloneWorldResult.Failure.WORLD_EXIST_FOLDER, replace("{world}").with(newWorldName));
        }
        if (isLoadedWorld(newWorldName)) {
            Logging.severe("World already loaded: " + newWorldName);
            return Result.failure(CloneWorldResult.Failure.WORLD_EXIST_LOADED, replace("{world}").with(newWorldName));
        }
        if (isWorld(newWorldName)) {
            Logging.severe("World already exist unloaded: " + newWorldName);
            return Result.failure(CloneWorldResult.Failure.WORLD_EXIST_UNLOADED, replace("{world}").with(newWorldName));
        }
        return Result.success();
    }

    private Result<CloneWorldResult.Success, CloneWorldResult.Failure>
            cloneWorldCopyFolder(@NotNull CloneWorldOptions options) {
        File worldFolder = options.world().getBukkitWorld().map(World::getWorldFolder).getOrNull(); // TODO: Check null?
        File newWorldFolder = new File(Bukkit.getWorldContainer(), options.newWorldName());
        return filesManipulator.copyFolder(worldFolder, newWorldFolder, CLONE_IGNORE_FILES).fold(
                exception -> Result.failure(CloneWorldResult.Failure.COPY_FAILED,
                        replace("{world}").with(options.world().getName()),
                        replace("{error}").with(exception.getMessage())),
                success -> Result.success());
    }

    private void cloneWorldTransferData(@NotNull CloneWorldOptions options, @NotNull LoadedMultiverseWorld newWorld) {
        LoadedMultiverseWorld world = options.world();
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
        dataTransfer.pasteAllTo(newWorld);
    }

    /**
     * Regenerates a world.
     *
     * @param options   The options for customizing the regeneration of a world.
     */
    public Result<RegenWorldResult.Success, RegenWorldResult.Failure> regenWorld(@NotNull RegenWorldOptions options) {
        // TODO: Teleport players out of world, and back in after regen
        LoadedMultiverseWorld world = options.world();

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

        CreateWorldOptions createWorldOptions = CreateWorldOptions.worldName(world.getName())
                .environment(world.getEnvironment())
                .generateStructures(world.canGenerateStructures().getOrElse(true))
                .generator(world.getGenerator())
                .seed(options.seed())
                .worldType(world.getWorldType().getOrElse(WorldType.NORMAL));

        var deleteResult = deleteWorld(world);
        if (deleteResult.isFailure()) {
            return Result.failure(RegenWorldResult.Failure.DELETE_FAILED, deleteResult.getReasonMessage());
        }

        var createResult = createWorld(createWorldOptions);
        if (createResult.isFailure()) {
            return Result.failure(RegenWorldResult.Failure.CREATE_FAILED, createResult.getReasonMessage());
        }

        getLoadedWorld(createWorldOptions.worldName()).peek(newWorld -> {
            dataTransfer.pasteAllTo(newWorld);
            saveWorldsConfig();
        });
        return Result.success(RegenWorldResult.Success.REGENERATED, replace("{world}").with(world.getName()));
    }

    /**
     * Creates a bukkit world.
     *
     * @param worldCreator  The world parameters.
     * @return The created world.
     */
    private Try<World> createBukkitWorld(WorldCreator worldCreator) {
        this.loadTracker.add(worldCreator.name());
        try {
            World world = worldCreator.createWorld();
            this.loadTracker.remove(worldCreator.name());
            if (world == null) {
                Logging.severe("Failed to create bukkit world: " + worldCreator.name());
                return Try.failure(new Exception("World created was null!")); // TODO: Localize this
            }
            Logging.fine("Bukkit created world: " + world.getName());
            return Try.success(world);
        } catch (Exception e) {
            this.loadTracker.remove(worldCreator.name());
            Logging.severe("Failed to create bukkit world: " + worldCreator.name());
            e.printStackTrace();
            return Try.failure(e);
        }
    }

    /**
     * Unloads a bukkit world.
     *
     * @param world The bukkit world to unload.
     * @return The unloaded world.
     */
    private Try<Void> unloadBukkitWorld(World world) {
        try {
            unloadTracker.add(world.getName());
            boolean unloadSuccess = Bukkit.unloadWorld(world, true);
            unloadTracker.remove(world.getName());
            if (unloadSuccess) {
                Logging.fine("Bukkit unloaded world: " + world.getName());
                return Try.success(null);
            }
            return Try.failure(new Exception("Is this the default world? You can't unload the default world!")); // TODO: Localize this, maybe with MultiverseException
        } catch (Exception e) {
            unloadTracker.remove(world.getName());
            Logging.severe("Failed to unload bukkit world: " + world.getName());
            e.printStackTrace();
            return Try.failure(e);
        }
    }

    /**
     * Gets a list of all potential worlds that can be loaded from the server folders.
     * Checks based on folder contents and name.
     *
     * @return A list of all potential worlds.
     */
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

    /**
     * Get a world that is not loaded.
     *
     * @param worldName The name of the world to get.
     * @return The world if it exists.
     */
    public Option<MultiverseWorld> getUnloadedWorld(@Nullable String worldName) {
        return isLoadedWorld(worldName) ? Option.none() : Option.of(worldsMap.get(worldName));
    }

    /**
     * Get a list of all worlds that are not loaded.
     *
     * @return A list of all worlds that are not loaded.
     */
    public Collection<MultiverseWorld> getUnloadedWorlds() {
        return worldsMap.values().stream().filter(world -> !world.isLoaded()).toList();
    }

    /**
     * Check if a world is a world that is not loaded.
     *
     * @param worldName The name of the world to check.
     * @return True if the world is a world that is not loaded.
     */
    public boolean isUnloadedWorld(@Nullable String worldName) {
        return !isLoadedWorld(worldName) && isWorld(worldName);
    }

    /**
     * Get a world that may or may not be loaded. It will an {@link LoadedMultiverseWorld} if the world is loaded,
     * otherwise returns an {@link MultiverseWorld} instance.
     *
     * @param worldName The name of the world to get.
     * @return The world if it exists.
     */
    public Option<MultiverseWorld> getWorld(@Nullable String worldName) {
        return getLoadedWorld(worldName).fold(() -> getUnloadedWorld(worldName), Option::of);
    }

    /**
     * <p>Get a list of all worlds that may or may not be loaded. It will an {@link LoadedMultiverseWorld} if the world
     * is loaded, otherwise you will get an {@link MultiverseWorld} instance.</p>
     *
     * <p>If you want only unloaded worlds, use {@link #getUnloadedWorlds()}. If you want only loaded worlds, use
     * {@link #getLoadedWorlds()}.</p>
     *
     * @return A list of all worlds that may or may not be loaded.
     */
    public Collection<MultiverseWorld> getWorlds() {
        return worldsMap.values().stream()
                .map(world -> getLoadedWorld(world).fold(() -> world, loadedWorld -> loadedWorld))
                .toList();
    }

    /**
     * Check if a world is a world is known to multiverse, but may or may not be loaded.
     *
     * @param worldName The name of the world to check.
     * @return True if the world is a world is known to multiverse, but may or may not be loaded.
     */
    public boolean isWorld(@Nullable String worldName) {
        return worldsMap.containsKey(worldName);
    }

    /**
     * Get a multiverse world that is loaded.
     *
     * @param world The bukkit world that should be loaded.
     * @return The multiverse world if it exists.
     */
    public Option<LoadedMultiverseWorld> getLoadedWorld(@Nullable World world) {
        return world == null ? Option.none() : Option.of(loadedWorldsMap.get(world.getName()));
    }

    /**
     * Get a multiverse world that is loaded.
     *
     * @param world The world that should be loaded.
     * @return The multiverse world if it exists.
     */
    public Option<LoadedMultiverseWorld> getLoadedWorld(@Nullable MultiverseWorld world) {
        return world == null ? Option.none() : Option.of(loadedWorldsMap.get(world.getName()));
    }

    /**
     * Get a multiverse world that is loaded.
     *
     * @param worldName The name of the world to get.
     * @return The multiverse world if it exists.
     */
    public Option<LoadedMultiverseWorld> getLoadedWorld(@Nullable String worldName) {
        return Option.of(loadedWorldsMap.get(worldName));
    }

    /**
     * Get a list of all multiverse worlds that are loaded.
     *
     * @return A list of all multiverse worlds that are loaded.
     */
    public Collection<LoadedMultiverseWorld> getLoadedWorlds() {
        return loadedWorldsMap.values();
    }

    /**
     * Check if a world is a multiverse world that is loaded.
     *
     * @param world The bukkit world to check.
     * @return True if the world is a multiverse world that is loaded.
     */
    public boolean isLoadedWorld(@Nullable World world) {
        return world != null && isLoadedWorld(world.getName());
    }

    /**
     * Check if a world is a multiverse world that is loaded.
     *
     * @param world The world to check.
     * @return True if the world is a multiverse world that is loaded.
     */
    public boolean isLoadedWorld(@Nullable MultiverseWorld world) {
        return world != null && isLoadedWorld(world.getName());
    }

    /**
     * Check if a world is a multiverse world that is loaded.
     *
     * @param worldName The name of the world to check.
     * @return True if the world is a multiverse world that is loaded.
     */
    public boolean isLoadedWorld(@Nullable String worldName) {
        return loadedWorldsMap.containsKey(worldName);
    }

    /**
     * Saves the worlds.yml config.
     *
     * @return true if it had successfully saved the file.
     */
    public boolean saveWorldsConfig() {
        return worldsConfigManager.save()
                .onFailure(failure -> {
                    Logging.severe("Failed to save worlds config: %s", failure);
                    failure.printStackTrace();
                })
                .isSuccess();
    }
}
