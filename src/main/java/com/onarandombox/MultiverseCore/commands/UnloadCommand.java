package com.onarandombox.MultiverseCore.commands;

import co.aikar.commands.BukkitCommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import com.onarandombox.MultiverseCore.api.MVWorld;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.commandtools.MVCommandManager;
import com.onarandombox.MultiverseCore.commandtools.MultiverseCommand;
import com.onarandombox.MultiverseCore.utils.MVCorei18n;
import jakarta.inject.Inject;
import org.jetbrains.annotations.NotNull;
import org.jvnet.hk2.annotations.Service;

@Service
@CommandAlias("mv")
public class UnloadCommand extends MultiverseCommand {

    private final MVWorldManager worldManager;

    @Inject
    public UnloadCommand(@NotNull MVCommandManager commandManager, @NotNull MVWorldManager worldManager) {
        super(commandManager);
        this.worldManager = worldManager;
    }

    @Subcommand("unload")
    @CommandPermission("multiverse.core.unload")
    @CommandCompletion("@mvworlds")
    @Syntax("<world>")
    @Description("{@@mv-core.unload.description}")
    public void onUnloadCommand(BukkitCommandIssuer issuer,

                                @Syntax("<world>")
                                @Description("{@@mv-core.unload.world.description}")
                                MVWorld world
    ) {
        issuer.sendInfo(MVCorei18n.UNLOAD_UNLOADING,
                "{world}", world.getColoredWorldString());

        //TODO API: Should be able to use MVWorld object directly
        if (!this.worldManager.unloadWorld(world.getName())) {
            issuer.sendError(MVCorei18n.UNLOAD_FAILURE,
                    "{world}", world.getColoredWorldString());
            return;
        }
        issuer.sendInfo(MVCorei18n.UNLOAD_SUCCESS,
                "{world}", world.getColoredWorldString());
    }
}
