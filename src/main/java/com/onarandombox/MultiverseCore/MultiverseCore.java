/******************************************************************************
 * Multiverse 2 Copyright (c) the Multiverse Team 2011.                       *
 * Multiverse 2 is licensed under the BSD License.                            *
 * For more information please check the README.md file included              *
 * with this project.                                                         *
 ******************************************************************************/

package com.onarandombox.MultiverseCore;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.dumptruckman.minecraft.util.Logging;
import com.onarandombox.MultiverseCore.anchor.AnchorManager;
import com.onarandombox.MultiverseCore.api.Destination;
import com.onarandombox.MultiverseCore.api.MVCore;
import com.onarandombox.MultiverseCore.api.MVWorld;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.commandtools.MVCommandManager;
import com.onarandombox.MultiverseCore.commandtools.MultiverseCommand;
import com.onarandombox.MultiverseCore.config.MVCoreConfigProvider;
import com.onarandombox.MultiverseCore.destination.DestinationsProvider;
import com.onarandombox.MultiverseCore.economy.MVEconomist;
import com.onarandombox.MultiverseCore.inject.InjectableListener;
import com.onarandombox.MultiverseCore.inject.PluginInjection;
import com.onarandombox.MultiverseCore.placeholders.MultiverseCorePlaceholders;
import com.onarandombox.MultiverseCore.utils.TestingMode;
import com.onarandombox.MultiverseCore.utils.metrics.MetricsConfigurator;
import com.onarandombox.MultiverseCore.world.WorldProperties;
import io.vavr.control.Try;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import me.main__.util.SerializationConfig.SerializationConfig;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jvnet.hk2.annotations.Service;

/**
 * The implementation of the Multiverse-{@link MVCore}.
 */
@Service
public class MultiverseCore extends JavaPlugin implements MVCore {
    private static final int PROTOCOL = 50;

    private ServiceLocator serviceLocator;
    @Inject
    private MVCoreConfigProvider configProvider;
    @Inject
    private Provider<MVWorldManager> worldManagerProvider;
    @Inject
    private Provider<AnchorManager> anchorManagerProvider;
    @Inject
    private Provider<MVCommandManager> commandManagerProvider;
    @Inject
    private Provider<DestinationsProvider> destinationsProviderProvider;
    @Inject
    private Provider<MetricsConfigurator> metricsConfiguratorProvider;
    @Inject
    private Provider<MVEconomist> economistProvider;

    // Counter for the number of plugins that have registered with us
    private int pluginCount;

    /**
     * This is the constructor for the MultiverseCore.
     */
    public MultiverseCore() {
        super();
    }

    @Override
    public void onLoad() {
        // Create our DataFolder
        getDataFolder().mkdirs();

        // Setup our Logging
        Logging.init(this);

        // Register our config classes
        SerializationConfig.registerAll(WorldProperties.class);
        SerializationConfig.initLogging(Logging.getLogger());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onEnable() {
        initializeDependencyInjection();

        // Load our configs first as we need them for everything else.
        this.loadConfigs();
        if (!getConfigProvider().isConfigLoaded()) {
            Logging.severe("Your configs were not loaded.");
            Logging.severe("Please check your configs and restart the server.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Logging.setShowingConfig(shouldShowConfig());

        var worldManager = worldManagerProvider.get();

        worldManager.getDefaultWorldGenerators();
        worldManager.loadDefaultWorlds();
        worldManager.loadWorlds(true);

        // Now set the firstspawnworld (after the worlds are loaded):
        worldManager.setFirstSpawnWorld(getConfigProvider().getConfig().getFirstSpawnLocation());
        MVWorld firstSpawnWorld = worldManager.getFirstSpawnWorld();
        if (firstSpawnWorld != null) {
            getConfigProvider().getConfig().setFirstSpawnLocation(firstSpawnWorld.getName());
        }

        //Setup economy here so vault is loaded
        this.loadEconomist();

        // Init all the other stuff
        this.loadAnchors();
        this.registerEvents();
        this.registerCommands();
        this.setUpLocales();
        this.registerDestinations();
        this.setupMetrics();
        this.loadPlaceholderAPIIntegration();
        this.saveMVConfig();
        this.logEnableMessage();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDisable() {
        this.saveAllConfigs();
        shutdownDependencyInjection();
        Logging.shutdown();
    }

    private void initializeDependencyInjection() {
        serviceLocator = PluginInjection.createServiceLocator(new MultiverseCorePluginBinder(this))
                .andThenTry(locator -> {
                    PluginInjection.enable(this, locator);
                })
                .getOrElseThrow(exception -> {
                    Logging.severe("Failed to initialize dependency injection");
                    getServer().getPluginManager().disablePlugin(this);
                    return new RuntimeException(exception);
                });
    }

    private void shutdownDependencyInjection() {
        if (serviceLocator != null) {
            PluginInjection.disable(this, serviceLocator);
            serviceLocator = null;
        }
    }

    private boolean shouldShowConfig() {
        return !getConfigProvider().getConfig().getSilentStart();
    }

    private void loadEconomist() {
        Try.run(() -> economistProvider.get())
                .onFailure(e -> Logging.severe("Failed to load economy integration", e));
    }

    private void loadAnchors() {
        Try.of(() -> anchorManagerProvider.get())
                .onSuccess(AnchorManager::loadAnchors)
                .onFailure(e -> Logging.severe("Failed to load anchors", e));
    }

    /**
     * Function to Register all the Events needed.
     */
    private void registerEvents() {
        var pluginManager = getServer().getPluginManager();

        Try.run(() -> serviceLocator.getAllServices(InjectableListener.class)
                        .forEach(listener -> pluginManager.registerEvents(listener, this)))
                .onFailure(e -> {
                    throw new RuntimeException("Failed to register listeners. Terminating...", e);
                });
    }

    /**
     * Register Multiverse-Core commands to Command Manager.
     */
    private void registerCommands() {
        Try.of(() -> commandManagerProvider.get())
                .andThenTry(commandManager -> {
                    serviceLocator.getAllServices(MultiverseCommand.class)
                            .forEach(commandManager::registerCommand);
                })
                .onFailure(e -> Logging.severe("Failed to register commands", e));
    }

    /**
     * Register locales
     */
    private void setUpLocales() {
        Try.of(() -> commandManagerProvider.get())
                .andThenTry(commandManager -> {
                    commandManager.usePerIssuerLocale(true, true);
                    commandManager.getLocales().addFileResClassLoader(this);
                    commandManager.getLocales().addMessageBundles("multiverse-core");
                })
                .onFailure(e -> Logging.severe("Failed to register locales", e));
    }

    /**
     * Register all the destinations.
     */
    private void registerDestinations() {
        Try.of(() -> destinationsProviderProvider.get())
                .andThenTry(destinationsProvider -> {
                    serviceLocator.getAllServices(Destination.class)
                            .forEach(destinationsProvider::registerDestination);
                })
                .onFailure(e -> Logging.severe("Failed to register destinations", e));
    }

    /**
     * Setup bstats Metrics.
     */
    private void setupMetrics() {
        if (TestingMode.isDisabled()) {
            // Load metrics
            Try.of(() -> metricsConfiguratorProvider.get())
                    .onFailure(e -> Logging.severe("Failed to setup metrics", e));
        } else {
            Logging.info("Metrics are disabled in testing mode.");
        }
    }

    /**
     * Logs the enable message.
     */
    private void logEnableMessage() {
        Logging.config("Version %s (API v%s) Enabled - By %s", this.getDescription().getVersion(), PROTOCOL, getAuthors());

        if (getConfigProvider().getConfig().isShowingDonateMessage()) {
            Logging.config("Help dumptruckman keep this project alive. Become a patron! https://www.patreon.com/dumptruckman");
            Logging.config("One time donations are also appreciated: https://www.paypal.me/dumptruckman");
        }
    }

    private void loadPlaceholderAPIIntegration() {
        if(getConfigProvider().getConfig().isRegisterPapiHook()
                && getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            Try.run(() -> serviceLocator.createAndInitialize(MultiverseCorePlaceholders.class))
                    .onFailure(e -> Logging.severe("Failed to load PlaceholderAPI integration.", e));
        }
    }

    private MVCoreConfigProvider getConfigProvider() {
        return configProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MVCore getCore() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getProtocolVersion() {
        return PROTOCOL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAuthors() {
        List<String> authorsList = this.getDescription().getAuthors();
        if (authorsList.size() == 0) {
            return "";
        }

        StringBuilder authors = new StringBuilder();
        authors.append(authorsList.get(0));

        for (int i = 1; i < authorsList.size(); i++) {
            if (i == authorsList.size() - 1) {
                authors.append(" and ").append(authorsList.get(i));
            } else {
                authors.append(", ").append(authorsList.get(i));
            }
        }

        return authors.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getPluginCount() {
        return this.pluginCount;
    }

    @NotNull
    @Override
    public Logger getLogger() {
        return Logging.getLogger();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementPluginCount() {
        this.pluginCount += 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void decrementPluginCount() {
        this.pluginCount -= 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadConfigs() {
        getConfigProvider().loadConfigs();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean saveMVConfig() {
        return getConfigProvider().saveConfig()
                .map(v -> true)
                .recover(e -> {
                    Logging.severe(e.getMessage(), e);
                    return false;
                })
                .get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean saveAllConfigs() {
        return this.saveMVConfig() && worldManagerProvider.get().saveWorldsConfig();
    }

    //TODO: REMOVE THIS STATIC CRAP - START
    private static final Map<String, String> teleportQueue = new HashMap<String, String>();

    /**
     * This method is used to add a teleportation to the teleportQueue.
     *
     * @param teleporter The name of the player that initiated the teleportation.
     * @param teleportee The name of the player that was teleported.
     */
    public static void addPlayerToTeleportQueue(String teleporter, String teleportee) {
        Logging.finest("Adding mapping '%s' => '%s' to teleport queue", teleporter, teleportee);
        teleportQueue.put(teleportee, teleporter);
    }

    /**
     * This method is used to find out who is teleporting a player.
     * @param playerName The teleported player (the teleportee).
     * @return The player that teleported the other one (the teleporter).
     */
    public static String getPlayerTeleporter(String playerName) {
        if (teleportQueue.containsKey(playerName)) {
            String teleportee = teleportQueue.get(playerName);
            teleportQueue.remove(playerName);
            return teleportee;
        }
        return null;
    }
    //TODO: REMOVE THIS STATIC CRAP - END


    // For testing purposes only //

    private File serverFolder = new File(System.getProperty("user.dir"));

    /**
     * This is for unit testing.
     *
     * @param loader The PluginLoader to use.
     * @param description The Description file to use.
     * @param dataFolder The folder that other datafiles can be found in.
     * @param file The location of the plugin.
     */
    public MultiverseCore(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
        super(loader, description, dataFolder, file);
    }

    /**
     * Gets the server's root-folder as {@link File}.
     *
     * @return The server's root-folder
     */
    public File getServerFolder() {
        return serverFolder;
    }

    /**
     * Sets this server's root-folder.
     *
     * @param newServerFolder The new server-root
     */
    public void setServerFolder(File newServerFolder) {
        if (!newServerFolder.isDirectory())
            throw new IllegalArgumentException("That's not a folder!");

        this.serverFolder = newServerFolder;
    }

    /**
     * Gets the best service from this plugin that implements the given contract or has the given implementation.
     *
     * @param contractOrImpl The contract or concrete implementation to get the best instance of
     * @param qualifiers The set of qualifiers that must match this service definition
     * @return An instance of the contract or impl if it is a service and is already instantiated, null otherwise
     * @throws MultiException if there was an error during service lookup
     */
    @Nullable
    public <T> T getService(@NotNull Class<T> contractOrImpl, Annotation... qualifiers) throws MultiException {
        var handle = serviceLocator.getServiceHandle(contractOrImpl, qualifiers);
        if (handle != null && handle.isActive()) {
            return handle.getService();
        }
        return null;
    }

    /**
     * Gets all services from this plugin that implement the given contract or have the given implementation and have
     * the provided qualifiers.
     *
     * @param contractOrImpl The contract or concrete implementation to get the best instance of
     * @param qualifiers The set of qualifiers that must match this service definition
     * @return A list of services implementing this contract or concrete implementation. May not return null, but may
     * return an empty list
     * @throws MultiException if there was an error during service lookup
     */
    @NotNull
    public <T> List<T> getAllServices(
            @NotNull Class<T> contractOrImpl,
            Annotation... qualifiers
    ) throws MultiException {
        var handles = serviceLocator.getAllServiceHandles(contractOrImpl, qualifiers);
        return handles.stream()
                .filter(ServiceHandle::isActive)
                .map(ServiceHandle::getService)
                .collect(Collectors.toList());
    }

//    @NotNull
//    @Override
//    public FileConfiguration getConfig() {
//        if (!getConfigProvider().isConfigLoaded()) {
//            getConfigProvider().loadConfigs();
//        }
//        return super.getConfig();
//    }
}
