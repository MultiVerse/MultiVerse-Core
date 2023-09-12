package org.mvplugins.multiverse.core.commands;

import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import com.dumptruckman.minecraft.util.Logging;
import jakarta.inject.Inject;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.jetbrains.annotations.NotNull;
import org.jvnet.hk2.annotations.Service;

import org.mvplugins.multiverse.core.commandtools.MVCommandIssuer;
import org.mvplugins.multiverse.core.commandtools.MVCommandManager;
import org.mvplugins.multiverse.core.commandtools.MultiverseCommand;
import org.mvplugins.multiverse.core.commandtools.flags.CommandFlag;
import org.mvplugins.multiverse.core.commandtools.flags.CommandFlagGroup;
import org.mvplugins.multiverse.core.commandtools.flags.CommandValueFlag;
import org.mvplugins.multiverse.core.commandtools.flags.ParsedCommandFlags;
import org.mvplugins.multiverse.core.utils.MVCorei18n;
import org.mvplugins.multiverse.core.worldnew.WorldManager;
import org.mvplugins.multiverse.core.worldnew.generators.GeneratorProvider;
import org.mvplugins.multiverse.core.worldnew.options.ImportWorldOptions;

@Service
@CommandAlias("mv")
class ImportCommand extends MultiverseCommand {

    private final WorldManager worldManager;

    private GeneratorProvider generatorProvider;
    private final CommandValueFlag<String> GENERATOR_FLAG = flag(CommandValueFlag
            .builder("--generator", String.class)
            .addAlias("-g")
            .completion(input -> generatorProvider.suggestGeneratorString(input))
            .build());

    private final CommandFlag NO_ADJUST_SPAWN_FLAG = flag(CommandFlag.builder("--no-adjust-spawn")
            .addAlias("-n")
            .build());

    @Inject
    ImportCommand(
            @NotNull MVCommandManager commandManager,
            @NotNull WorldManager worldManager,
            @NotNull GeneratorProvider generatorProvider) {
        super(commandManager);
        this.worldManager = worldManager;
        this.generatorProvider = generatorProvider;
    }

    @Subcommand("import")
    @CommandPermission("multiverse.core.import")
    @CommandCompletion("@mvworlds:scope=potential  @flags:groupName=mvimportcommand")
    @Syntax("<name> <env> --generator [generator[:id]] --adjust-spawn")
    @Description("{@@mv-core.import.description")
    void onImportCommand(
            MVCommandIssuer issuer,

            @Conditions("worldname:scope=new")
            @Syntax("<name>")
            @Description("{@@mv-core.import.name.description}")
            String worldName,

            @Syntax("<env>")
            @Description("{@@mv-core.import.env.description}")
            World.Environment environment,

            @Optional
            @Syntax("--generator [generator[:id]] --adjust-spawn")
            @Description("{@@mv-core.import.other.description}")
            String[] flags) {
        ParsedCommandFlags parsedFlags = parseFlags(flags);

        issuer.sendInfo(MVCorei18n.IMPORT_IMPORTING, "{world}", worldName);
        worldManager.importWorld(ImportWorldOptions.worldName(worldName)
                .environment(environment)
                .generator(parsedFlags.flagValue(GENERATOR_FLAG, String.class))
                .useSpawnAdjust(!parsedFlags.hasFlag(NO_ADJUST_SPAWN_FLAG)))
                .onSuccess(newWorld -> {
                    Logging.fine("World import success: " + newWorld);
                    issuer.sendInfo(MVCorei18n.IMPORT_SUCCESS, "{world}", newWorld.getName());
                })
                .onFailure(failure -> {
                    Logging.fine("World import failure: " + failure);
                    issuer.sendError(failure.getFailureMessage());
                });
    }
}
