package com.onarandombox.MultiverseCore.commands;

import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.HelpCommand;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import com.onarandombox.MultiverseCore.commandtools.MVCommandManager;
import com.onarandombox.MultiverseCore.commandtools.MultiverseCommand;
import jakarta.inject.Inject;
import org.jetbrains.annotations.NotNull;
import org.jvnet.hk2.annotations.Service;

@Service
@CommandAlias("mv")
public class UsageCommand extends MultiverseCommand {

    @Inject
    public UsageCommand(@NotNull MVCommandManager commandManager) {
        super(commandManager);
    }

    @HelpCommand
    @Subcommand("help")
    @CommandPermission("multiverse.core.help")
    @CommandCompletion("@commands:mv")
    @Syntax("[filter] [page]")
    @Description("{@@mv-core.usage.description}")
    public void onUsageCommand(CommandHelp help) {
        if (help.getIssuer().isPlayer()) {
            // Prevent flooding the chat
            help.setPerPage(4);
        }
        this.commandManager.showUsage(help);
    }
}
