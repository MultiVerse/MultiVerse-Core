package com.onarandombox.MultiverseCore.commandtools;

import java.util.*;
import java.util.stream.Collectors;

import co.aikar.commands.BukkitCommandCompletionContext;
import co.aikar.commands.BukkitCommandIssuer;
import co.aikar.commands.PaperCommandCompletions;
import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorld;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.GameRule;
import org.jetbrains.annotations.NotNull;

public class MVCommandCompletions extends PaperCommandCompletions {
    protected final MVCommandManager commandManager;
    private final MultiverseCore plugin;
    private final MVWorldManager worldManager;

    public MVCommandCompletions(MVCommandManager mvCommandManager, MultiverseCore plugin) {
        super(mvCommandManager);
        this.commandManager = mvCommandManager;
        this.plugin = plugin;
        this.worldManager = plugin.getMVWorldManager();

        registerAsyncCompletion("destinations", this::suggestDestinations);
        registerAsyncCompletion("flags", this::suggestFlags);
        registerAsyncCompletion("mvworlds", this::suggestMVWorlds);
        // Only updates on first load, helps with lag
        registerStaticCompletion("gamerules", this::suggestGamerules);
    }

    private Collection<String> suggestDestinations(BukkitCommandCompletionContext context) {
        if (context.hasConfig("playerOnly") && !context.getIssuer().isPlayer()) {
            return Collections.emptyList();
        }

        return this.plugin.getDestinationsProvider()
                .suggestDestinations((BukkitCommandIssuer)context.getIssuer(), context.getInput());
    }

    private Collection<String> suggestFlags(@NotNull BukkitCommandCompletionContext context) {
        return this.commandManager.getFlagsManager().suggest(
                context.getConfig("groupName", ""), context.getContextValue(String[].class));
    }

    private Collection<String> suggestMVWorlds(BukkitCommandCompletionContext context) {
        if (context.hasConfig("playerOnly") && !context.getIssuer().isPlayer()) {
            return Collections.emptyList();
        }

        String scope = context.getConfig("scope", "loaded");
        List<String> worlds = new ArrayList<>();
        switch (scope) {
            case "both":
                worlds.addAll(worldManager.getUnloadedWorlds());
            case "loaded":
                worldManager.getMVWorlds()
                        .stream()
                        .map(MVWorld::getName)
                        .forEach(worlds::add);
                break;
            case "unloaded":
                worlds.addAll(worldManager.getUnloadedWorlds());
                break;
        }

        return worlds;
    }

    private Collection<String> suggestGamerules() {
        return Arrays.stream(GameRule.values()).map(GameRule::getName).collect(Collectors.toList());
    }
}
