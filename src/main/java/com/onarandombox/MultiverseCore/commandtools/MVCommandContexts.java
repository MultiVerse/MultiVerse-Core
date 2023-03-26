package com.onarandombox.MultiverseCore.commandtools;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import co.aikar.commands.BukkitCommandExecutionContext;
import co.aikar.commands.BukkitCommandIssuer;
import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.PaperCommandContexts;
import co.aikar.commands.contexts.ContextResolver;
import com.google.common.base.Strings;
import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorld;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.commandtools.context.GameRuleValue;
import com.onarandombox.MultiverseCore.commandtools.context.MVConfigValue;
import com.onarandombox.MultiverseCore.configuration.MVConfigNodes;
import com.onarandombox.MultiverseCore.destination.DestinationsProvider;
import com.onarandombox.MultiverseCore.destination.ParsedDestination;
import com.onarandombox.MultiverseCore.display.filters.ContentFilter;
import com.onarandombox.MultiverseCore.display.filters.DefaultContentFilter;
import com.onarandombox.MultiverseCore.display.filters.RegexContentFilter;
import com.onarandombox.MultiverseCore.utils.PlayerFinder;
import io.github.townyadvanced.commentedconfiguration.setting.CommentedNode;
import io.github.townyadvanced.commentedconfiguration.setting.TypedValueNode;
import io.github.townyadvanced.commentedconfiguration.setting.ValueNode;
import jakarta.inject.Inject;
import org.bukkit.GameRule;
import org.bukkit.entity.Player;
import org.jvnet.hk2.annotations.Service;

@Service
public class MVCommandContexts extends PaperCommandContexts {

    private final DestinationsProvider destinationsProvider;
    private final MVWorldManager worldManager;

    @Inject
    public MVCommandContexts(
            MVCommandManager mvCommandManager,
            DestinationsProvider destinationsProvider,
            MVWorldManager worldManager
    ) {
        super(mvCommandManager);
        this.destinationsProvider = destinationsProvider;
        this.worldManager = worldManager;

        registerIssuerOnlyContext(BukkitCommandIssuer.class, BukkitCommandExecutionContext::getIssuer);
        registerOptionalContext(ContentFilter.class, this::parseContentFilter);
        registerContext(ParsedDestination.class, this::parseDestination);
        registerContext(GameRule.class, this::parseGameRule);
        registerContext(GameRuleValue.class, this::parseGameRuleValue);
        registerContext(MVConfigValue.class, this::parseMVConfigValue);
        registerIssuerAwareContext(MVWorld.class, this::parseMVWorld);
        registerIssuerAwareContext(MVWorld[].class, this::parseMVWorldArray);
        registerIssuerAwareContext(Player.class, this::parsePlayer);
        registerIssuerAwareContext(Player[].class, this::parsePlayerArray);
    }

    private ContentFilter parseContentFilter(BukkitCommandExecutionContext context) {
        if (Strings.isNullOrEmpty(context.getFirstArg())) {
            return DefaultContentFilter.get();
        }
        String filterString = context.popFirstArg();
        return RegexContentFilter.fromString(filterString);
    }

    private ParsedDestination<?> parseDestination(BukkitCommandExecutionContext context) {
        String destination = context.popFirstArg();
        if (Strings.isNullOrEmpty(destination)) {
            throw new InvalidCommandArgument("No destination specified.");
        }

        ParsedDestination<?> parsedDestination = destinationsProvider.parseDestination(destination);
        if (parsedDestination == null) {
            throw new InvalidCommandArgument("The destination " + destination + " is not valid.");
        }

        return parsedDestination;
    }

    private GameRule<?> parseGameRule(BukkitCommandExecutionContext context) {
        String gameRuleName = context.popFirstArg();
        if (Strings.isNullOrEmpty(gameRuleName)) {
            throw new InvalidCommandArgument("No game rule specified.");
        }

        GameRule<?> gameRule = GameRule.getByName(gameRuleName);
        if (gameRule == null) {
            throw new InvalidCommandArgument("The game rule " + gameRuleName + " is not valid.");
        }

        return gameRule;
    }

    private GameRuleValue parseGameRuleValue(BukkitCommandExecutionContext context) {
        GameRule<?> gameRule = (GameRule<?>) context.getResolvedArg(GameRule.class);
        if (gameRule == null) {
            throw new InvalidCommandArgument("No game rule specified.");
        }
        String valueString = context.getFirstArg();
        if (Strings.isNullOrEmpty(valueString)) {
            throw new InvalidCommandArgument("No game rule value specified.");
        }

        ContextResolver<?, BukkitCommandExecutionContext> resolver = getResolver(gameRule.getType());
        if (resolver == null) {
            return new GameRuleValue(valueString);
        }

        Object resolvedValue = resolver.getContext(context);
        if (resolvedValue == null) {
            throw new InvalidCommandArgument("The game rule value " + valueString + " is not valid for game rule " + gameRule.getName() + ".");
        }

        return new GameRuleValue(resolvedValue);
    }

    private MVConfigValue parseMVConfigValue(BukkitCommandExecutionContext context) {
        String configName = (String) context.getResolvedArg(String.class);
        if (Strings.isNullOrEmpty(configName)) {
            throw new InvalidCommandArgument("No config name specified.");
        }
        Optional<CommentedNode> node = MVConfigNodes.getNodes().findNode(configName);
        if (node.isEmpty()) {
            throw new InvalidCommandArgument("The config " + configName + " is not valid.");
        }

        String valueString = context.getFirstArg();
        if (Strings.isNullOrEmpty(valueString)) {
            throw new InvalidCommandArgument("No config value specified.");
        }

        if (!(node.get() instanceof TypedValueNode)) {
            context.popFirstArg();
            return new MVConfigValue(valueString);
        }

        ContextResolver<?, BukkitCommandExecutionContext> resolver = getResolver(((TypedValueNode<?>) node.get()).getType());
        if (resolver == null) {
            context.popFirstArg();
            return new MVConfigValue(valueString);
        }

        Object resolvedValue = resolver.getContext(context);
        if (resolvedValue == null) {
            throw new InvalidCommandArgument("The config value " + valueString + " is not valid for config " + configName + ".");
        }
        return new MVConfigValue(resolvedValue);
    }

    private MVWorld parseMVWorld(BukkitCommandExecutionContext context) {
        String resolve = context.getFlagValue("resolve", "");

        // Get world based on sender only
        if (resolve.equals("issuerOnly")) {
            if (context.getIssuer().isPlayer()) {
                return worldManager.getMVWorld(context.getIssuer().getPlayer().getWorld());
            }
            if (context.isOptional()) {
                return null;
            }
            throw new InvalidCommandArgument("This command can only be used by a player in a Multiverse World.");
        }

        String worldName = context.getFirstArg();
        MVWorld world = worldManager.getMVWorld(worldName);

        // Get world based on input, fallback to sender if input is not a world
        if (resolve.equals("issuerAware")) {
            if (world != null) {
                context.popFirstArg();
                return world;
            }
            if (context.getIssuer().isPlayer()) {
                return worldManager.getMVWorld(context.getIssuer().getPlayer().getWorld());
            }
            if (context.isOptional()) {
                return null;
            }
            throw new InvalidCommandArgument("Player is not in a Multiverse World.");
        }

        // Get world based on input only
        if (world != null) {
            context.popFirstArg();
            return world;
        }
        if (!context.isOptional()) {
            return null;
        }
        throw new InvalidCommandArgument("World " + worldName + " is not a loaded multiverse world.");
    }

    private MVWorld[] parseMVWorldArray(BukkitCommandExecutionContext context) {
        String resolve = context.getFlagValue("resolve", "");

        MVWorld playerWorld = null;
        if (context.getIssuer().isPlayer()) {
            playerWorld = worldManager.getMVWorld(context.getIssuer().getPlayer().getWorld());
        }

        // Get world based on sender only
        if (resolve.equals("issuerOnly")) {
            if (playerWorld != null) {
                return new MVWorld[]{playerWorld};
            }
            if (context.isOptional()) {
                return null;
            }
            throw new InvalidCommandArgument("This command can only be used by a player in a Multiverse World.");
        }

        String worldStrings = context.getFirstArg();
        String[] worldNames = worldStrings.split(",");
        Set<MVWorld> worlds = new HashSet<>(worldNames.length);
        for (String worldName : worldNames) {
            if ("*".equals(worldName)) {
                worlds.addAll(worldManager.getMVWorlds());
                break;
            }
            MVWorld world = worldManager.getMVWorld(worldName);
            if (world == null) {
                throw new InvalidCommandArgument("World " + worldName + " is not a loaded multiverse world.");
            }
            worlds.add(world);
        }

        // Get world based on input, fallback to sender if input is not a world
        if (resolve.equals("issuerAware")) {
            if (!worlds.isEmpty()) {
                context.popFirstArg();
                return worlds.toArray(new MVWorld[0]);
            }
            if (playerWorld != null) {
                return new MVWorld[]{playerWorld};
            }
            if (context.isOptional()) {
                return null;
            }
            throw new InvalidCommandArgument("Player is not in a Multiverse World.");
        }

        // Get world based on input only
        if (!worlds.isEmpty()) {
            context.popFirstArg();
            return worlds.toArray(new MVWorld[0]);
        }
        if (!context.isOptional()) {
            return null;
        }
        throw new InvalidCommandArgument("World " + worldStrings + " is not a loaded multiverse world.");
    }

    private Player parsePlayer(BukkitCommandExecutionContext context) {
        String resolve = context.getFlagValue("resolve", "");

        // Get player based on sender only
        if (resolve.equals("issuerOnly")) {
            if (context.getIssuer().isPlayer()) {
                return context.getIssuer().getPlayer();
            }
            if (context.isOptional()) {
                return null;
            }
            throw new InvalidCommandArgument("This command can only be used by a player.");
        }

        String playerIdentifier = context.getFirstArg();
        Player player = PlayerFinder.get(playerIdentifier, context.getSender());

        // Get player based on input, fallback to sender if input is not a player
        if (resolve.equals("issuerAware")) {
            if (player != null) {
                context.popFirstArg();
                return player;
            }
            if (context.getIssuer().isPlayer()) {
                return context.getIssuer().getPlayer();
            }
            if (context.isOptional()) {
                return null;
            }
            throw new InvalidCommandArgument("Invalid player: " + playerIdentifier
                    + ". Either specify an online player or use this command as a player.");
        }

        // Get player based on input only
        if (player != null) {
            context.popFirstArg();
            return player;
        }
        if (!context.isOptional()) {
            return null;
        }
        throw new InvalidCommandArgument("Player " + playerIdentifier + " not found.");
    }


    private Player[] parsePlayerArray(BukkitCommandExecutionContext context) {
        String resolve = context.getFlagValue("resolve", "");

        // Get player based on sender only
        if (resolve.equals("issuerOnly")) {
            if (context.getIssuer().isPlayer()) {
                return new Player[]{context.getIssuer().getPlayer()};
            }
            if (context.isOptional()) {
                return null;
            }
            throw new InvalidCommandArgument("This command can only be used by a player.");
        }

        String playerIdentifier = context.getFirstArg();
        Player[] players = PlayerFinder.getMulti(playerIdentifier, context.getSender()).toArray(new Player[0]);

        // Get player based on input, fallback to sender if input is not a player
        if (resolve.equals("issuerAware")) {
            if (players.length > 0) {
                context.popFirstArg();
                return players;
            }
            if (context.getIssuer().isPlayer()) {
                return new Player[]{context.getIssuer().getPlayer()};
            }
            if (context.isOptional()) {
                return null;
            }
            throw new InvalidCommandArgument("Invalid player: " + playerIdentifier
                    + ". Either specify an online player or use this command as a player.");
        }

        // Get player based on input only
        if (players.length > 0) {
            context.popFirstArg();
            return players;
        }
        if (!context.isOptional()) {
            return null;
        }
        throw new InvalidCommandArgument("Player " + playerIdentifier + " not found.");
    }
}
