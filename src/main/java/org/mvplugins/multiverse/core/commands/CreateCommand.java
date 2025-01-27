package org.mvplugins.multiverse.core.commands;

import java.util.Collections;

import co.aikar.commands.ACFUtil;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import com.dumptruckman.minecraft.util.Logging;
import com.google.common.collect.Lists;
import jakarta.inject.Inject;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.jvnet.hk2.annotations.Service;

import org.mvplugins.multiverse.core.commandtools.MVCommandManager;
import org.mvplugins.multiverse.core.commandtools.flag.CommandFlag;
import org.mvplugins.multiverse.core.commandtools.flag.CommandValueFlag;
import org.mvplugins.multiverse.core.commandtools.flag.ParsedCommandFlags;
import org.mvplugins.multiverse.core.locale.MVCorei18n;
import org.mvplugins.multiverse.core.locale.message.MessageReplacement.Replace;
import org.mvplugins.multiverse.core.utils.result.Attempt.Failure;
import org.mvplugins.multiverse.core.world.LoadedMultiverseWorld;
import org.mvplugins.multiverse.core.world.WorldManager;
import org.mvplugins.multiverse.core.world.generators.GeneratorProvider;
import org.mvplugins.multiverse.core.world.options.CreateWorldOptions;
import org.mvplugins.multiverse.core.world.reasons.CreateFailureReason;

import static org.mvplugins.multiverse.core.locale.message.MessageReplacement.replace;

@Service
@CommandAlias("mv")
final class CreateCommand extends CoreCommand {

    private final WorldManager worldManager;
    private GeneratorProvider generatorProvider;

    private final CommandValueFlag<String> seedFlag = flag(CommandValueFlag.builder("--seed", String.class)
            .addAlias("-s")
            .completion(input -> Collections.singleton(String.valueOf(ACFUtil.RANDOM.nextLong())))
            .build());

    private final CommandValueFlag<String> generatorFlag = flag(CommandValueFlag
            .builder("--generator", String.class)
            .addAlias("-g")
            .completion(input -> generatorProvider.suggestGeneratorString(input))
            .build());

    private final CommandValueFlag<WorldType> worldTypeFlag = flag(CommandValueFlag
            .enumBuilder("--world-type", WorldType.class)
            .addAlias("-t")
            .build());

    private final CommandFlag noAdjustSpawnFlag = flag(CommandFlag.builder("--no-adjust-spawn")
            .addAlias("-n")
            .build());

    private final CommandFlag noStructuresFlag = flag(CommandFlag.builder("--no-structures")
            .addAlias("-a")
            .build());

    private final CommandValueFlag<Biome> biomeFlag = flag(CommandValueFlag.builder("--biome", Biome.class)
            .addAlias("-b")
            .completion(input -> Lists.newArrayList(Registry.BIOME).stream()
                    .filter(biome -> biome != Biome.CUSTOM)
                    .map(biome -> biome.getKey().getKey())
                    .toList())
            .context(biomeStr -> Registry.BIOME.get(NamespacedKey.minecraft(biomeStr)))
            .build());

    @Inject
    CreateCommand(
            @NotNull MVCommandManager commandManager,
            @NotNull WorldManager worldManager,
            @NotNull GeneratorProvider generatorProvider) {
        super(commandManager);
        this.worldManager = worldManager;
        this.generatorProvider = generatorProvider;
    }

    @CommandAlias("mvcreate|mvc")
    @Subcommand("create")
    @CommandPermission("multiverse.core.create")
    @CommandCompletion("@empty @environments @flags:groupName=mvcreatecommand")
    @Syntax("<name> <environment> [--seed <seed> --generator <generator[:id]> --world-type <worldtype> --adjust-spawn "
            + "--no-structures --biome <biome>]")
    @Description("{@@mv-core.create.description}")
    void onCreateCommand(
            CommandIssuer issuer,

            @Syntax("<name>")
            @Description("{@@mv-core.create.name.description}")
            String worldName,

            @Syntax("<environment>")
            @Description("{@@mv-core.create.environment.description}")
            World.Environment environment,

            @Optional
            @Syntax("[--seed <seed> --generator <generator[:id]> --world-type <worldtype> --adjust-spawn "
                    + "--no-structures --biome <biome>]")
            @Description("{@@mv-core.create.flags.description}")
            String[] flags) {
        ParsedCommandFlags parsedFlags = parseFlags(flags);

        messageWorldDetails(issuer, worldName, environment, parsedFlags);

        MVCorei18n.CREATE_LOADING.sendInfo(issuer);

        worldManager.createWorld(CreateWorldOptions.worldName(worldName)
                .biome(parsedFlags.flagValue(biomeFlag, Biome.CUSTOM))
                .environment(environment)
                .seed(parsedFlags.flagValue(seedFlag))
                .worldType(parsedFlags.flagValue(worldTypeFlag, WorldType.NORMAL))
                .useSpawnAdjust(!parsedFlags.hasFlag(noAdjustSpawnFlag))
                .generator(parsedFlags.flagValue(generatorFlag, ""))
                .generateStructures(!parsedFlags.hasFlag(noStructuresFlag)))
                .onSuccess(newWorld -> messageSuccess(issuer, newWorld))
                .onFailure(failure -> messageFailure(issuer, failure));
    }

    private void messageWorldDetails(CommandIssuer issuer, String worldName,
                                     World.Environment environment, ParsedCommandFlags parsedFlags) {
        MVCorei18n.CREATE_PROPERTIES.sendInfo(issuer,
                replace("{worldName}").with(worldName));
        MVCorei18n.CREATE_PROPERTIES_ENVIRONMENT.sendInfo(issuer,
                replace("{environment}").with(environment.name()));
        MVCorei18n.CREATE_PROPERTIES_SEED.sendInfo(issuer,
                replace("{seed}").with(parsedFlags.flagValue(seedFlag, "RANDOM")));
        MVCorei18n.CREATE_PROPERTIES_WORLDTYPE.sendInfo(issuer,
                replace("{worldType}").with(parsedFlags.flagValue(worldTypeFlag, WorldType.NORMAL).name()));
        MVCorei18n.CREATE_PROPERTIES_ADJUSTSPAWN.sendInfo(issuer,
                replace("{adjustSpawn}").with(String.valueOf(!parsedFlags.hasFlag(noAdjustSpawnFlag))));
        if (parsedFlags.hasFlag(biomeFlag)) {
            MVCorei18n.CREATE_PROPERTIES_BIOME.sendInfo(issuer,
                    replace("{biome}").with(parsedFlags.flagValue(biomeFlag, Biome.CUSTOM).name()));
        }
        if (parsedFlags.hasFlag(generatorFlag)) {
            MVCorei18n.CREATE_PROPERTIES_GENERATOR.sendInfo(issuer,
                    replace("{generator}").with(parsedFlags.flagValue(generatorFlag)));
        }
        MVCorei18n.CREATE_PROPERTIES_STRUCTURES.sendInfo(issuer,
                replace("{structures}").with(String.valueOf(!parsedFlags.hasFlag(noStructuresFlag))));
    }

    private void messageSuccess(CommandIssuer issuer, LoadedMultiverseWorld newWorld) {
        Logging.fine("World create success: " + newWorld);
        MVCorei18n.CREATE_SUCCESS.sendInfo(issuer, Replace.WORLD.with(newWorld.getName()));
    }

    private void messageFailure(CommandIssuer issuer, Failure<LoadedMultiverseWorld, CreateFailureReason> failure) {
        Logging.fine("World create failure: " + failure);
        failure.getFailureMessage().sendError(issuer);
    }
}
