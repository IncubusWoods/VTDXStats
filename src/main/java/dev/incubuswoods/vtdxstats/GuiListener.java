package dev.incubuswoods.vtdxstats;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

final class GuiListener implements Listener {
    private final VTDXStats plugin;
    GuiListener(VTDXStats plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder h = e.getView().getTopInventory().getHolder();
        if (!(h instanceof Guis.VtdxHolder vh)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        switch (vh.type()) {
            case "overview" -> handleOverview(p, (Guis.OverviewHolder) h, slot);
            case "repartir" -> handleRepartir(p, (Guis.RepartirHolder) h, slot, e.isLeftClick(), e.isRightClick(), e.isShiftClick());
            case "admin_players" -> handleAdminPlayers(p, (Guis.AdminPlayersHolder) h, slot);
            case "admin_player" -> handleAdminPlayer(p, (Guis.AdminPlayerHolder) h, slot);
        }
    }

    private void handleOverview(Player p, Guis.OverviewHolder h, int slot) {
        StatConfig c = plugin.cfg();
        if (slot == c.slot("gui.overview.slots.repartir",49)) { plugin.guis().openRepartir(p, h.owner); return; }
        if (slot == c.slot("gui.overview.slots.cerrar",53)) p.closeInventory();
    }

    private void handleRepartir(Player p, Guis.RepartirHolder h, int slot, boolean left, boolean right, boolean shift) {
        StatConfig c = plugin.cfg();
        if (slot == c.slot("gui.repartir.slots.cerrar",49)) { plugin.guis().openOverview(p, h.owner); return; }
        String stat = h.statSlots.get(slot);
        if (stat == null) return;
        if (!p.getUniqueId().equals(h.owner) && !p.hasPermission("vtdxstats.admin")) return;
        if (plugin.stats().isStatDisabled(h.owner, stat)) { p.sendMessage(GuiUtil.mm(plugin.getConfig().getString("messages.stat-disabled","<red>Esa stat est? desactivada por un administrador.</red>"))); return; }
        int amount = shift ? 9999 : (right ? 5 : (left ? 1 : 0));
        if (amount <= 0) return;
        int added = plugin.stats().addPoints(h.owner, stat, amount);
        if (added <= 0) p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
        else p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        plugin.guis().openRepartir(p, h.owner);
    }

    private void handleAdminPlayers(Player p, Guis.AdminPlayersHolder h, int slot) {
        StatConfig c = plugin.cfg();
        if (slot == c.slot("gui.admin-players.slots.close",49)) { p.closeInventory(); return; }
        if (slot == c.slot("gui.admin-players.slots.previous",45) && h.page > 0) { plugin.guis().openAdminPlayers(p, h.page - 1); return; }
        if (slot == c.slot("gui.admin-players.slots.next",53)) { plugin.guis().openAdminPlayers(p, h.page + 1); return; }
        UUID target = h.targets.get(slot);
        if (target != null) plugin.guis().openAdminPlayer(p, target, h.page);
    }

    private void handleAdminPlayer(Player p, Guis.AdminPlayerHolder h, int slot) {
        StatConfig c = plugin.cfg();
        if (slot == c.slot("gui.admin-player.slots.close",53)) { p.closeInventory(); return; }
        if (slot == c.slot("gui.admin-player.slots.back",45)) { plugin.guis().openAdminPlayers(p, h.page); return; }
        if (slot == c.slot("gui.admin-player.slots.reset",49)) {
            plugin.stats().reset(h.target);
            p.sendMessage(GuiUtil.mm(plugin.getConfig().getString("messages.reset-other","<green>Stats reiniciados para <white>{player}</white>.</green>")
                    .replace("{player}", OptionalName.of(h.target))));
            plugin.guis().openAdminPlayer(p, h.target, h.page);
            return;
        }
        String stat = h.statSlots.get(slot);
        if (stat == null) return;
        plugin.stats().toggleStatDisabled(h.target, stat);
        plugin.guis().openAdminPlayer(p, h.target, h.page);
    }

    static final class OptionalName { static String of(UUID u){ var op = org.bukkit.Bukkit.getOfflinePlayer(u); return op.getName()==null?u.toString().substring(0,8):op.getName(); } }
}
