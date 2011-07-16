package com.onarandombox.MultiverseCore.commands;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.onarandombox.MultiverseCore.MultiverseCore;
import com.pneumaticraft.commandhandler.Command;

public class SpawnCommand extends Command {

    public SpawnCommand(MultiverseCore plugin) {
        super(plugin);
        this.commandName = "Spawn";
        this.commandDesc = "Teleports target player to the Spawn Point of the world they are in.";
        this.commandUsage = "/mvspawn" + ChatColor.GOLD + " [PLAYER]";
        this.minimumArgLength = 0;
        this.maximumArgLength = 1;
        this.commandKeys.add("mvspawn");
        this.commandKeys.add("mv spawn");
        this.commandKeys.add("mvs");
        this.permission = "multiverse.world.spawn";
        this.opRequired = false;
    }

    @Override
    public void runCommand(CommandSender sender, List<String> args) {
        Player player = null;
        if (sender instanceof Player) {
            player = (Player) sender;
        }
        // If a persons name was passed in, you must be A. the console, or B have permissions
        if (args.size() == 1) {
            if (player != null && !((MultiverseCore) this.plugin).getPermissions().hasPermission(player, "multiverse.world.spawn.other", true)) {
                sender.sendMessage("You don't have permission to teleport another player to spawn. (multiverse.world.spawn.other)");
                return;
            }
            Player target = this.plugin.getServer().getPlayer(args.get(0));
            if (target != null) {
                target.sendMessage("Teleporting to this world's spawn...");
                target.teleport(target.getWorld().getSpawnLocation());
                if (player != null) {
                    target.sendMessage("You were teleported by: " + ChatColor.YELLOW + player.getName());
                } else {
                    target.sendMessage("You were teleported by: " + ChatColor.LIGHT_PURPLE + "the console");
                }
            } else {
                sender.sendMessage(args.get(0) + " is not logged on right now!");
            }
        } else {
            if (player != null && !((MultiverseCore) this.plugin).getPermissions().hasPermission(player, "multiverse.world.spawn.self", true)) {
                sender.sendMessage("You don't have permission to teleport yourself to spawn. (multiverse.world.spawn.self)");
                return;
            }
            if (player != null) {
                player.sendMessage("Teleporting to this world's spawn...");
                player.teleport(player.getWorld().getSpawnLocation());
            } else {
                sender.sendMessage("From the console, you must provide a PLAYER.");
            }
        }
    }
}
