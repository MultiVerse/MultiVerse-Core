/******************************************************************************
 * Multiverse 2 Copyright (c) the Multiverse Team 2011.                       *
 * Multiverse 2 is licensed under the BSD License.                            *
 * For more information please check the README.md file included              *
 * with this project.                                                         *
 ******************************************************************************/

package com.onarandombox.MultiverseCore.utils;

import com.fernferret.allpay.GenericBank;
import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;

public class PermissionTools {
    private MultiverseCore plugin;

    public PermissionTools(MultiverseCore plugin) {
        this.plugin = plugin;
    }

    public void addToParentPerms(String permString) {
        String permStringChopped = permString.replace(".*", "");

        String[] seperated = permStringChopped.split("\\.");
        String parentPermString = getParentPerm(seperated);
        if (parentPermString == null) {
            addToRootPermission("*", permStringChopped);
            addToRootPermission("*.*", permStringChopped);
            return;
        }
        Permission parentPermission = this.plugin.getServer().getPluginManager().getPermission(parentPermString);
        // Creat parent and grandparents
        if (parentPermission == null) {
            parentPermission = new Permission(parentPermString);
            this.plugin.getServer().getPluginManager().addPermission(parentPermission);

            this.addToParentPerms(parentPermString);
        }
        // Create actual perm.
        Permission actualPermission = this.plugin.getServer().getPluginManager().getPermission(permString);
        // Extra check just to make sure the actual one is added
        if (actualPermission == null) {

            actualPermission = new Permission(permString);
            this.plugin.getServer().getPluginManager().addPermission(actualPermission);
        }
        if (!parentPermission.getChildren().containsKey(permString)) {
            parentPermission.getChildren().put(actualPermission.getName(), true);
            this.plugin.getServer().getPluginManager().recalculatePermissionDefaults(parentPermission);
        }
    }

    private void addToRootPermission(String rootPerm, String permStringChopped) {
        Permission rootPermission = this.plugin.getServer().getPluginManager().getPermission(rootPerm);
        if (rootPermission == null) {
            rootPermission = new Permission(rootPerm);
            this.plugin.getServer().getPluginManager().addPermission(rootPermission);
        }
        rootPermission.getChildren().put(permStringChopped + ".*", true);
        this.plugin.getServer().getPluginManager().recalculatePermissionDefaults(rootPermission);
    }

    /**
     * If the given permission was 'multiverse.core.tp.self', this would return 'multiverse.core.tp.*'.
     *
     * @param separatedPermissionString The array of a dot separated perm string.
     * @return The dot separated parent permission string.
     */
    private String getParentPerm(String[] separatedPermissionString) {
        if (separatedPermissionString.length == 1) {
            return null;
        }
        String returnString = "";
        for (int i = 0; i < separatedPermissionString.length - 1; i++) {
            returnString += separatedPermissionString[i] + ".";
        }
        return returnString + "*";
    }

    public boolean playerHasMoneyToEnter(MultiverseWorld fromWorld, MultiverseWorld toWorld, Player teleporter, Player teleportee, boolean pay) {
        if (teleporter == null) {
            return true;
        }
        // Only check payments if it's a different world:
        if (!toWorld.equals(fromWorld)) {
            // If the player does not have to pay, return now.
            if (this.plugin.getMVPerms().hasPermission(teleporter, toWorld.getExemptPermission().getName(), true)) {
                return true;
            }
            GenericBank bank = plugin.getBank();
            String errString = "You need " + bank.getFormattedAmount(teleporter, toWorld.getPrice(), toWorld.getCurrency()) + " to send " + teleportee + " to " + toWorld.getColoredWorldString();
            if (teleportee.equals(teleporter)) {
                errString = "You need " + bank.getFormattedAmount(teleporter, toWorld.getPrice(), toWorld.getCurrency()) + " to enter " + toWorld.getColoredWorldString();
            }
            if (!bank.hasEnough(teleporter, toWorld.getPrice(), toWorld.getCurrency(), errString)) {
                return false;
            } else if(pay) {
                bank.pay(teleporter, toWorld.getPrice(), toWorld.getCurrency());
            }
        }
        return true;
    }


    /**
     * Checks to see if player can go to a world given their current status.
     * <p>
     * The return is a little backwards, and will return a value safe for event.setCancelled.
     *
     * @param fromWorld  The MultiverseWorld they are in.
     * @param toWorld    The MultiverseWorld they want to go to.
     * @param teleporter The player that wants to travel.
     * @return True if they can't go to the world, False if they can.
     */
    public boolean playerCanGoFromTo(MultiverseWorld fromWorld, MultiverseWorld toWorld, Player teleporter, Player teleportee) {
        // The console can send anyone anywhere
        if (teleporter == null) {
            return true;
        }
        if (toWorld != null) {
            if (!this.plugin.getMVPerms().canEnterWorld(teleporter, toWorld)) {
                if (teleportee.equals(teleporter)) {
                    teleporter.sendMessage("You don't have access to go here...");
                } else {
                    teleporter.sendMessage("You can't send " + teleportee.getName() + " here...");
                }

                return false;
            }
        } else {
            //TODO: Determine if this value is false because a world didn't exist
            // or if it was because a world wasn't imported.
            return true;
        }
        if (fromWorld != null) {
            if (fromWorld.getWorldBlacklist().contains(toWorld.getName())) {
                if (teleportee.equals(teleporter)) {
                    teleporter.sendMessage("You don't have access to go to " + toWorld.getColoredWorldString() + " from " + fromWorld.getColoredWorldString());
                } else {
                    teleporter.sendMessage("You don't have access to send " + teleportee.getName() + " from " + fromWorld.getColoredWorldString() + " to " + toWorld.getColoredWorldString());
                }
                return false;
            }
        }
        return true;
    }
    
    /**
     * Checks to see if a player should bypass game mode restrictions.
     * 
     * @param toWorld world travelling to.
     * @param teleportee player travelling.
     * @return True if they should bypass restrictions
     */
    public boolean playerCanIgnoreGameModeRestriction(MultiverseWorld toWorld, Player teleportee) {
        if (toWorld != null) {
            return this.plugin.getMVPerms().canIgnoreGameModeRestriction(teleportee, toWorld);
        } else {
            //TODO: Determine if this value is false because a world didn't exist
            // or if it was because a world wasn't imported.
            return true;
        }
        return true;
    }
}
