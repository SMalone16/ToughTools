package com.smalone.toughwoodtools;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class ToughTools extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ToughTools enabled: empowering wooden pickaxes and axes.");
    }

    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent e) {
        ItemStack item = e.getItem();
        if (!isToughWoodTool(item)) {
            return;
        }

        empower(item);
        e.setDamage(0); // never consume durability on our empowered tools
    }

    @EventHandler
    public void onBlockDamage(BlockDamageEvent e) {
        ItemStack item = e.getItemInHand();
        if (!isToughWoodTool(item)) {
            return;
        }

        empower(item);
        e.setInstaBreak(true); // instantly breaks targeted blocks while swinging
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        ItemStack result = e.getCurrentItem();
        if (!isToughWoodTool(result)) {
            return;
        }

        empower(result);
        e.setCurrentItem(result);
    }

    private boolean isToughWoodTool(ItemStack item) {
        if (item == null) {
            return false;
        }

        Material type = item.getType();
        return type == Material.WOOD_PICKAXE || type == Material.WOOD_AXE;
    }

    private void empower(ItemStack item) {
        if (item == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        // keep the tool pristine
        item.setDurability((short) 0);
        meta.setUnbreakable(true);

        // grant maximum combat potential to the holder
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE,
                new AttributeModifier(DAMAGE_ID, "toughtools-damage", 100.0, Operation.ADD_NUMBER));
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED,
                new AttributeModifier(SPEED_ID, "toughtools-speed", 10.0, Operation.ADD_NUMBER));

        // give the tools extreme efficiency-like behaviour when used for mining
        item.setItemMeta(meta);

        // cap their enchantments to the highest vanilla levels so existing items stay powerful
        item.addUnsafeEnchantment(Enchantment.DIG_SPEED, 10);
        item.addUnsafeEnchantment(Enchantment.DURABILITY, 10);
    }

    private static final UUID DAMAGE_ID = UUID.fromString("77763ca6-4df5-4b0b-8a6c-f78b7d0f5126");
    private static final UUID SPEED_ID = UUID.fromString("1e54d2f5-77bd-4f40-a25f-42595fd4bf3c");
}
