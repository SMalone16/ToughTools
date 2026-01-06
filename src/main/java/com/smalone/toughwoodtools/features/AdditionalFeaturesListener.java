package com.smalone.toughwoodtools.features;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class AdditionalFeaturesListener implements Listener {

    private static final double FIST_DIAMOND_CHANCE = 0.25D;
    private static final double CHICKEN_EXTRA_SPAWN_CHANCE = 0.30D;
    private static final double ENDERMAN_EXTRA_SPAWN_CHANCE = 0.35D;
    private static final long FAIRY_DURATION_TICKS = 200L;
    private static final long FAIRY_DROP_PERIOD_TICKS = 20L;
    private static final double TORCH_AGGRO_RADIUS = 16.0D;
    private static final long TORCH_AGGRO_PERIOD_TICKS = 20L;

    private final Plugin plugin;
    private final Random random = new Random();
    private final Map<UUID, FairyState> fairyStates = new HashMap<UUID, FairyState>();

    public AdditionalFeaturesListener(Plugin plugin) {
        this.plugin = plugin;
    }

    public void startTasks() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tickTorchAggro();
            }
        }, TORCH_AGGRO_PERIOD_TICKS, TORCH_AGGRO_PERIOD_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (type != Material.STONE && type != Material.COAL_ORE) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand != null && hand.getType() != Material.AIR) {
            return;
        }

        if (random.nextDouble() < FIST_DIAMOND_CHANCE) {
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.DIAMOND, 1));
        }
    }

    @EventHandler
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        if (message == null || !message.equalsIgnoreCase("fairy mode")) {
            return;
        }

        final Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                activateFairyMode(player);
            }
        });

        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntityType() != EntityType.CHICKEN) {
            return;
        }

        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        PlayerInventory inv = killer.getInventory();
        if (!hasRawChicken(inv) || hasSword(inv)) {
            return;
        }

        Material[] swords = new Material[] { Material.STONE_SWORD, Material.IRON_SWORD, Material.GOLD_SWORD, Material.DIAMOND_SWORD };
        Material chosen = swords[random.nextInt(swords.length)];
        event.getDrops().add(new ItemStack(chosen, 1));
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        EntityType type = event.getEntityType();
        SpawnReason reason = event.getSpawnReason();
        if (reason == SpawnReason.CUSTOM) {
            return;
        }

        if (type == EntityType.CHICKEN && (reason == SpawnReason.NATURAL || reason == SpawnReason.CHUNK_GEN)) {
            if (random.nextDouble() < CHICKEN_EXTRA_SPAWN_CHANCE) {
                spawnExtraCreature(event.getEntity(), EntityType.CHICKEN);
            }
            return;
        }

        if (type == EntityType.ENDERMAN && (reason == SpawnReason.NATURAL || reason == SpawnReason.CHUNK_GEN)) {
            World world = event.getLocation().getWorld();
            long time = world != null ? world.getTime() : 0L;
            if (time >= 13000L && time <= 23000L && random.nextDouble() < ENDERMAN_EXTRA_SPAWN_CHANCE) {
                spawnExtraCreature(event.getEntity(), EntityType.ENDERMAN);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEndermanDamagePlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (!(event.getDamager() instanceof Enderman)) {
            return;
        }

        Player player = (Player) event.getEntity();
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        int baseX = center.getBlockX();
        int baseY = center.getBlockY() - 1;
        int baseZ = center.getBlockZ();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = world.getBlockAt(baseX + dx, baseY, baseZ + dz);
                Material type = block.getType();
                if (type == Material.DIRT || type == Material.STONE) {
                    block.setType(Material.CONCRETE);
                    // Concrete colors in 1.12 use data values: 15 = black, 14 = red, 10 = purple.
                    byte data = pickConcreteData();
                    block.setData(data);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        if (!hasReceivedStarterKit(player)) {
            giveFrontierKit(player);
            markStarterKit(player);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        giveFrontierKit(player);
    }

    private void activateFairyMode(final Player player) {
        final UUID uuid = player.getUniqueId();
        FairyState existing = fairyStates.get(uuid);
        if (existing != null) {
            cancelFairyTasks(existing);
        }

        final FairyState state = new FairyState();
        state.hadAllowFlight = existing != null ? existing.hadAllowFlight : player.getAllowFlight();
        fairyStates.put(uuid, state);

        player.setAllowFlight(true);
        try {
            player.setFlying(true);
        } catch (Exception ignored) {
            // Some servers may not allow forcing flight; allowFlight is the critical part.
        }

        state.dropTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new BukkitRunnable() {
            private int drops = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                player.getWorld().dropItemNaturally(player.getLocation(),
                        new ItemStack(Material.GLOWSTONE_DUST, 1));
                drops++;
                if (drops >= 10) {
                    cancel();
                }
            }
        }, 0L, FAIRY_DROP_PERIOD_TICKS);

        state.endTask = plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                endFairyMode(player, uuid, state);
            }
        }, FAIRY_DURATION_TICKS);
    }

    private void endFairyMode(Player player, UUID uuid, FairyState state) {
        if (fairyStates.get(uuid) != state) {
            return;
        }
        fairyStates.remove(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        if (player.isFlying()) {
            player.setFlying(false);
        }
        if (!state.hadAllowFlight) {
            player.setAllowFlight(false);
        }
    }

    private void cancelFairyTasks(FairyState state) {
        if (state.dropTask != null) {
            state.dropTask.cancel();
        }
        if (state.endTask != null) {
            state.endTask.cancel();
        }
    }

    private boolean hasRawChicken(PlayerInventory inv) {
        if (inv == null) {
            return false;
        }
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == Material.RAW_CHICKEN) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSword(PlayerInventory inv) {
        if (inv == null) {
            return false;
        }
        for (ItemStack item : inv.getContents()) {
            if (item == null) {
                continue;
            }
            Material type = item.getType();
            if (type == Material.WOOD_SWORD || type == Material.STONE_SWORD || type == Material.IRON_SWORD
                    || type == Material.GOLD_SWORD || type == Material.DIAMOND_SWORD) {
                return true;
            }
        }
        return false;
    }

    private void spawnExtraCreature(Entity original, EntityType type) {
        if (original == null) {
            return;
        }
        Location base = original.getLocation();
        World world = base.getWorld();
        if (world == null) {
            return;
        }

        double dx = (random.nextDouble() * 2.0D - 1.0D) * 2.0D;
        double dz = (random.nextDouble() * 2.0D - 1.0D) * 2.0D;

        Location spawnLoc = base.clone().add(dx, 0.0D, dz);
        Entity spawned = world.spawnEntity(spawnLoc, type);
        if (spawned instanceof Creature && original instanceof Creature) {
            ((Creature) spawned).setTarget(((Creature) original).getTarget());
        }
    }

    private byte pickConcreteData() {
        int pick = random.nextInt(3);
        if (pick == 0) {
            return 15; // Black
        }
        if (pick == 1) {
            return 14; // Red
        }
        return 10; // Purple
    }

    private void giveFrontierKit(Player player) {
        PlayerInventory inventory = player.getInventory();
        Map<Integer, ItemStack> leftovers = inventory.addItem(
                new ItemStack(Material.WOOD, 64, (short) 0), // Oak planks in 1.12
                new ItemStack(Material.GLASS, 12),
                new ItemStack(Material.CHEST, 1),
                new ItemStack(Material.SADDLE, 1),
                new ItemStack(Material.BED, 1)
        );

        if (leftovers != null && !leftovers.isEmpty()) {
            for (ItemStack item : leftovers.values()) {
                if (item != null) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
        }

        spawnHorseInFront(player);
    }

    private void spawnHorseInFront(Player player) {
        Location base = player.getLocation();
        World world = base.getWorld();
        if (world == null) {
            return;
        }

        Vector direction = base.getDirection();
        if (direction == null || direction.lengthSquared() == 0.0D) {
            direction = new Vector(1, 0, 0);
        }
        Location horseLoc = base.clone().add(direction.normalize().multiply(2.0D));
        Location highest = world.getHighestBlockAt(horseLoc).getLocation().add(0.5D, 1.0D, 0.5D);

        Entity entity = world.spawnEntity(highest, EntityType.HORSE);
        if (entity instanceof Horse) {
            ((Horse) entity).setTamed(true);
        }
    }

    private boolean hasReceivedStarterKit(Player player) {
        return plugin.getConfig().getBoolean("starter-kits." + player.getUniqueId().toString(), false);
    }

    private void markStarterKit(Player player) {
        plugin.getConfig().set("starter-kits." + player.getUniqueId().toString(), true);
        plugin.saveConfig();
    }

    private void tickTorchAggro() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null) {
                continue;
            }
            if (!isHoldingTorch(player)) {
                continue;
            }

            for (Entity entity : player.getNearbyEntities(TORCH_AGGRO_RADIUS, TORCH_AGGRO_RADIUS, TORCH_AGGRO_RADIUS)) {
                if (entity instanceof Enderman) {
                    ((Enderman) entity).setTarget(player);
                }
            }
        }
    }

    private boolean isHoldingTorch(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        return isTorch(main) || isTorch(off);
    }

    private boolean isTorch(ItemStack item) {
        return item != null && item.getType() == Material.TORCH;
    }

    private static class FairyState {
        boolean hadAllowFlight;
        BukkitTask dropTask;
        BukkitTask endTask;
    }
}
