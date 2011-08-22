package com.onarandombox.utils;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Vehicle;
import org.bukkit.util.Vector;

public class LocationManipulation {
    private static Map<String, Integer> orientationInts = new HashMap<String, Integer>();
    static {
        orientationInts.put("n", 90);
        orientationInts.put("ne", 135);
        orientationInts.put("e", 180);
        orientationInts.put("se", 225);
        orientationInts.put("s", 270);
        orientationInts.put("sw", 315);
        orientationInts.put("w", 0);
        orientationInts.put("nw", 45);

    }

    /**
     * Convert a Location into a Colon separated string to allow us to store it in text.
     * 
     * @param location
     * @return
     */
    public static String locationToString(Location location) {
        StringBuilder l = new StringBuilder();
        l.append(location.getBlockX() + ":");
        l.append(location.getBlockY() + ":");
        l.append(location.getBlockZ() + ":");
        l.append(location.getYaw() + ":");
        l.append(location.getPitch());
        return l.toString();
    }

    /**
     * Convert a String to a Location.
     * 
     * @param world
     * @param xStr
     * @param yStr
     * @param zStr
     * @param yawStr
     * @param pitchStr
     * @return
     */
    public Location stringToLocation(World world, String xStr, String yStr, String zStr, String yawStr, String pitchStr) {
        double x = Double.parseDouble(xStr);
        double y = Double.parseDouble(yStr);
        double z = Double.parseDouble(zStr);
        float yaw = Float.valueOf(yawStr).floatValue();
        float pitch = Float.valueOf(pitchStr).floatValue();

        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Returns a colored string with the coords
     * @param l
     * @return
     */
    public static String strCoords(Location l) {
        String result = "";
        DecimalFormat df = new DecimalFormat();
        df.setMinimumFractionDigits(0);
        df.setMaximumFractionDigits(2);
        result += ChatColor.WHITE + "X: " + ChatColor.AQUA + df.format(l.getX()) + " ";
        result += ChatColor.WHITE + "Y: " + ChatColor.AQUA + df.format(l.getY()) + " ";
        result += ChatColor.WHITE + "Z: " + ChatColor.AQUA + df.format(l.getZ()) + " ";
        result += ChatColor.WHITE + "P: " + ChatColor.GOLD + df.format(l.getPitch()) + " ";
        result += ChatColor.WHITE + "Y: " + ChatColor.GOLD + df.format(l.getYaw()) + " ";
        return result;
    }
    /**
     * Converts a location to a printable readable formatted string including pitch/yaw
     * @param l
     * @return
     */
    public static String strCoordsRaw(Location l) {
        String result = "";
        DecimalFormat df = new DecimalFormat();
        df.setMinimumFractionDigits(0);
        df.setMaximumFractionDigits(2);
        result += "X: " + df.format(l.getX()) + " ";
        result += "Y: " + df.format(l.getY()) + " ";
        result += "Z: " + df.format(l.getZ()) + " ";
        result += "P: " + df.format(l.getPitch()) + " ";
        result += "Y: " + df.format(l.getYaw()) + " ";
        return result;
    }

    /**
     * Return the NESW Direction a Location is facing.
     * 
     * @param location
     * @return
     */
    public static String getDirection(Location location) {
        double r = (location.getYaw() % 360) + 180;
        // Remember, these numbers are every 45 degrees with a 22.5 offset, to detect boundaries.
        String dir;
        if (r < 22.5)
            dir = "n";
        else if (r < 67.5)
            dir = "ne";
        else if (r < 112.5)
            dir = "e";
        else if (r < 157.5)
            dir = "se";
        else if (r < 202.5)
            dir = "s";
        else if (r < 247.5)
            dir = "sw";
        else if (r < 292.5)
            dir = "w";
        else if (r < 337.5)
            dir = "nw";
        else
            dir = "n";

        return dir;
    }
    /**
     * Returns the float yaw position for the given cardianl direction
     * @param orientation
     * @return
     */
    public static float getYaw(String orientation) {
        if (orientation == null) {
            return 0;
        }
        if (orientationInts.containsKey(orientation.toLowerCase())) {
            return orientationInts.get(orientation.toLowerCase());
        }
        return 0;
    }
    /**
     * Returns a speed float from a given vector.
     * @param v
     * @return
     */
    public static float getSpeed(Vector v) {
        return (float) Math.sqrt(v.getX() * v.getX() + v.getZ() * v.getZ());
    }

    // X, Y, Z
    // -W/+E,0, -N/+S
    /**
     * Returns a translated vector from the given direction
     * @param v
     * @param direction
     * @return
     */
    public static Vector getTranslatedVector(Vector v, String direction) {
        if (direction == null) {
            return v;
        }
        float speed = getSpeed(v);
        float halfSpeed = (float) (speed / 2.0);
        // TODO: Mathmatacize this:
        if (direction.equalsIgnoreCase("n")) {
            return new Vector(0, 0, -1 * speed);
        } else if (direction.equalsIgnoreCase("ne")) {
            return new Vector(halfSpeed, 0, -1 * halfSpeed);
        } else if (direction.equalsIgnoreCase("e")) {
            return new Vector(speed, 0, 0);
        } else if (direction.equalsIgnoreCase("se")) {
            return new Vector(halfSpeed, 0, halfSpeed);
        } else if (direction.equalsIgnoreCase("s")) {
            return new Vector(0, 0, speed);
        } else if (direction.equalsIgnoreCase("sw")) {
            return new Vector(-1 * halfSpeed, 0, halfSpeed);
        } else if (direction.equalsIgnoreCase("w")) {
            return new Vector(-1 * speed, 0, 0);
        } else if (direction.equalsIgnoreCase("nw")) {
            return new Vector(-1 * halfSpeed, 0, -1 * halfSpeed);
        }
        return v;
    }

    /**
     * Returns the next Location that an entity is traveling at
     * 
     * @param v
     * @return
     */
    public static Location getNextBlock(Vehicle v) {
        Vector vector = v.getVelocity();
        Location location = v.getLocation();
        int x = vector.getX() < 0 ? vector.getX() == 0 ? 0 : -1 : 1;
        int z = vector.getZ() < 0 ? vector.getZ() == 0 ? 0 : -1 : 1;
        return location.add(x, 0, z);
    }
}
