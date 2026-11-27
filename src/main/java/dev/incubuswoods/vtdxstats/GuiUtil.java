package dev.incubuswoods.vtdxstats;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

final class GuiUtil {
    static final MiniMessage MM = MiniMessage.miniMessage();

    static Component mm(String s) { return MM.deserialize(s == null ? "" : s); }
    static String plain(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }

    static ItemStack item(Material mat, String name, List<String> lore) { return item(mat, name, lore, null); }

    static ItemStack item(Material mat, String name, List<String> lore, String itemModel) {
        ItemStack it = new ItemStack(mat == null ? Material.STONE : mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(mm(name));
            if (lore != null && !lore.isEmpty()) {
                List<Component> lc = new ArrayList<>();
                for (String l : lore) lc.add(mm(l));
                meta.lore(lc);
            }
            applyItemModel(meta, itemModel);
            it.setItemMeta(meta);
        }
        return it;
    }

    static void applyItemModel(ItemMeta meta, String model) {
        if (meta == null || model == null || model.isBlank()) return;
        try {
            String[] parts = model.trim().split(":", 2);
            org.bukkit.NamespacedKey key = parts.length == 2
                    ? new org.bukkit.NamespacedKey(parts[0].toLowerCase(java.util.Locale.ROOT), parts[1])
                    : org.bukkit.NamespacedKey.minecraft(model.trim().toLowerCase(java.util.Locale.ROOT));
            meta.setItemModel(key);
        } catch (Throwable ignored) {}
    }

    static void fill(Inventory inv, Material filler) {
        ItemStack it = item(filler, "<gray> </gray>", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, it);
    }

    static int clampSlot(int slot, int size) { return slot >= 0 && slot < size ? slot : -1; }
}
