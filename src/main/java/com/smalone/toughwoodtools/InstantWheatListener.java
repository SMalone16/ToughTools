package com.smalone.toughwoodtools;

import org.bukkit.Material;
import org.bukkit.CropState;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.material.Crops;

public class InstantWheatListener implements Listener {

    private final ToughTools plugin;

    public InstantWheatListener(ToughTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Block placed = e.getBlockPlaced();

        // Wheat in 1.12 is the CROPS block
        if (placed.getType() != Material.CROPS) {
            return;
        }

        // schedule: 20 ticks ~ 1 second
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // recheck: block might have been broken/changed in the meantime
            if (placed.getType() != Material.CROPS) return;

            BlockState state = placed.getState();
            if (!(state.getData() instanceof Crops)) return;

            Crops data = (Crops) state.getData();
            data.setState(CropState.RIPE);   // fully grown
            state.setData(data);
            state.update(true, false);       // apply without physics
        }, 20L);
    }
}
