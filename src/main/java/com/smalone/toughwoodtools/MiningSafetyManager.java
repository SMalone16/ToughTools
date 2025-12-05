package com.smalone.toughwoodtools;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;

public class MiningSafetyManager {

    private final ToughTools plugin;
    private final Set<Material> collapseWhitelist;
    private static final int REQUIRED_AIR_RUN = 6;
    private static final int MAX_VERTICAL_HEIGHT = 5;
    private static final int MAX_HORIZONTAL_HEIGHT = 6;
    private static final int MAX_HORIZONTAL_DISTANCE = 6;
    private static final int MAX_FALLING_BLOCKS = 90;

    public MiningSafetyManager(ToughTools plugin, Set<Material> collapseWhitelist) {
        this.plugin = plugin;
        this.collapseWhitelist = collapseWhitelist == null ? EnumSet.noneOf(Material.class) : collapseWhitelist;
    }

    public boolean handleShaftAndTunnel(Block broken, Player player) {
        if (!collapseWhitelist.contains(broken.getType())) {
            return false;
        }

        Location origin = broken.getLocation();
        World world = broken.getWorld();

        if (triggerVerticalIfNeeded(world, origin, player)) {
            return true;
        }

        if (triggerHorizontalIfNeeded(world, origin, Axis.X, player)) {
            return true;
        }

        return triggerHorizontalIfNeeded(world, origin, Axis.Z, player);
    }

    private boolean triggerVerticalIfNeeded(World world, Location origin, Player player) {
        if (!isDeepUnderground(origin, 6)) {
            return false;
        }

        int airAbove = countVerticalAirAbove(world, origin, REQUIRED_AIR_RUN);
        if (airAbove < REQUIRED_AIR_RUN) {
            return false;
        }

        int surfaceY = world.getHighestBlockYAt(origin.getBlockX(), origin.getBlockZ());
        int depthBelowSurface = surfaceY - origin.getBlockY();
        triggerVerticalCaveIn(world, origin, player, airAbove, depthBelowSurface);
        return true;
    }

    private boolean triggerHorizontalIfNeeded(World world, Location origin, Axis axis, Player player) {
        HorizontalRun run = countHorizontalAirRun(world, origin, axis, REQUIRED_AIR_RUN);
        if (run.total < REQUIRED_AIR_RUN) {
            return false;
        }

        int direction = run.positive >= run.negative ? 1 : -1;
        boolean hasSupport = hasWoodSupport(world, origin, axis, MAX_HORIZONTAL_DISTANCE, direction);
        if (hasSupport) {
            return false;
        }

        triggerTunnelCaveIn(world, origin, axis, direction, Math.min(run.total, MAX_HORIZONTAL_DISTANCE), player, hasSupport);
        return true;
    }

    private boolean isDeepUnderground(Location loc, int minDepth) {
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }
        int surfaceY = world.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ());
        int y = loc.getBlockY();
        return y <= surfaceY - minDepth;
    }

    private int countVerticalAirAbove(World world, Location origin, int maxLength) {
        int count = 0;
        int originY = origin.getBlockY();
        int x = origin.getBlockX();
        int z = origin.getBlockZ();

        for (int dy = 1; dy <= maxLength; dy++) {
            int y = originY + dy;
            if (y >= world.getMaxHeight()) {
                break;
            }
            if (world.getBlockAt(x, y, z).getType() != Material.AIR) {
                break;
            }
            count++;
        }

        return count;
    }

    private HorizontalRun countHorizontalAirRun(World world, Location origin, Axis axis, int maxLength) {
        int positive = countDirectionalAir(world, origin, axis, maxLength, 1);
        int negative = countDirectionalAir(world, origin, axis, maxLength, -1);
        return new HorizontalRun(positive, negative, positive + negative + 1);
    }

    private int countDirectionalAir(World world, Location origin, Axis axis, int maxLength, int direction) {
        int count = 0;
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        for (int d = 1; d <= maxLength; d++) {
            int x = axis == Axis.X ? ox + (direction * d) : ox;
            int z = axis == Axis.Z ? oz + (direction * d) : oz;
            Block block = world.getBlockAt(x, oy, z);
            if (block.getType() != Material.AIR) {
                break;
            }
            count++;
        }
        return count;
    }

    private boolean hasWoodSupport(World world, Location origin, Axis axis, int maxDistance, int direction) {
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        for (int d = 1; d <= maxDistance; d++) {
            int centerX = axis == Axis.X ? ox + (direction * d) : ox;
            int centerZ = axis == Axis.Z ? oz + (direction * d) : oz;

            for (int dy = -1; dy <= 1; dy++) {
                for (int offset = -1; offset <= 1; offset++) {
                    int x = axis == Axis.X ? centerX : centerX + offset;
                    int z = axis == Axis.Z ? centerZ : centerZ + offset;
                    Block block = world.getBlockAt(x, oy + dy, z);
                    if (block.getType() == Material.WOOD) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void triggerVerticalCaveIn(World world, Location center, Player player, int airRunLength, int depthBelowSurface) {
        int spawned = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 1; dy <= MAX_VERTICAL_HEIGHT; dy++) {
                    if (spawned >= MAX_FALLING_BLOCKS) {
                        return;
                    }
                    int x = center.getBlockX() + dx;
                    int y = center.getBlockY() + dy;
                    int z = center.getBlockZ() + dz;
                    Block target = world.getBlockAt(x, y, z);
                    if (target.getType() != Material.AIR) {
                        continue;
                    }
                    spawnFallingBlock(world, target.getLocation(), Material.DIRT, (byte) 0);
                    spawned++;
                }
            }
        }

        if (plugin.isDebugCaveIns() && player != null) {
            player.sendMessage(ChatColor.GRAY + "[DEBUG] " + ChatColor.YELLOW
                    + "Vertical cave-in triggered at " + center.getBlockX() + ", " + center.getBlockY() + ", "
                    + center.getBlockZ() + " (depth=" + depthBelowSurface + ", airRun=" + airRunLength + ")");
        }
    }

    private void triggerTunnelCaveIn(World world, Location origin, Axis axis, int direction, int airRunLength, Player player, boolean hasSupport) {
        int spawned = 0;
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        for (int d = 1; d <= airRunLength; d++) {
            int baseX = axis == Axis.X ? ox + (direction * d) : ox;
            int baseZ = axis == Axis.Z ? oz + (direction * d) : oz;

            for (int side = -1; side <= 1; side++) {
                int x = axis == Axis.X ? baseX : baseX + side;
                int z = axis == Axis.Z ? baseZ : baseZ + side;
                spawned = collapseColumn(world, x, oy, z, spawned);
                if (spawned >= MAX_FALLING_BLOCKS) {
                    return;
                }
            }
        }

        if (plugin.isDebugCaveIns() && player != null) {
            player.sendMessage(ChatColor.GRAY + "[DEBUG] " + ChatColor.YELLOW
                    + "Tunnel cave-in triggered along axis " + axis.name()
                    + " at " + origin.getBlockX() + ", " + origin.getBlockY() + ", " + origin.getBlockZ()
                    + " (airRun=" + airRunLength + ", supportFound=" + hasSupport + ")");
        }
    }

    private int collapseColumn(World world, int x, int baseY, int z, int spawnedSoFar) {
        for (int dy = 1; dy <= MAX_HORIZONTAL_HEIGHT; dy++) {
            if (spawnedSoFar >= MAX_FALLING_BLOCKS) {
                return spawnedSoFar;
            }
            int y = baseY + dy;
            if (y >= world.getMaxHeight()) {
                return spawnedSoFar;
            }
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type == Material.AIR) {
                continue;
            }

            if (!collapseWhitelist.contains(type)) {
                continue;
            }

            byte data = block.getData();
            block.setType(Material.AIR);
            spawnFallingBlock(world, new Location(world, x + 0.5D, y + 0.0D, z + 0.5D), type, data);
            spawnedSoFar++;
        }
        return spawnedSoFar;
    }

    private void spawnFallingBlock(World world, Location location, Material type, byte data) {
        FallingBlock falling = world.spawnFallingBlock(location, type, data);
        try {
            falling.setDropItem(false);
            falling.setHurtEntities(false);
        } catch (NoSuchMethodError ignored) {
            // Older API revisions may not support these toggles; gravity is sufficient.
        }
    }

    public enum Axis {
        X,
        Z
    }

    private static class HorizontalRun {
        final int positive;
        final int negative;
        final int total;

        HorizontalRun(int positive, int negative, int total) {
            this.positive = positive;
            this.negative = negative;
            this.total = total;
        }
    }
}
