package com.onarandombox.MultiverseCore.destination.core;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import co.aikar.commands.BukkitCommandIssuer;
import com.onarandombox.MultiverseCore.api.Destination;
import com.onarandombox.MultiverseCore.api.Teleporter;
import com.onarandombox.MultiverseCore.utils.PlayerFinder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BedDestination implements Destination<BedDestinationInstance> {
    public static final String OWN_BED_STRING = "playerbed";

    public BedDestination() {
    }

    @Override
    public @NotNull String getIdentifier() {
        return "b";
    }

    @Override
    public @Nullable BedDestinationInstance getDestinationInstance(String destParams) {
        Player player = PlayerFinder.get(destParams);
        if (player == null && !destParams.equals(OWN_BED_STRING)) {
            return null;
        }
        return new BedDestinationInstance(player);
    }

    @Override
    public @NotNull Collection<String> suggestDestinations(@NotNull BukkitCommandIssuer issuer, @Nullable String destParams) {
        List<String> collect = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        collect.add(OWN_BED_STRING);
        return collect;
    }

    @Override
    public boolean checkTeleportSafety() {
        return false;
    }

    @Override
    public @Nullable Teleporter getTeleporter() {
        return null;
    }
}
