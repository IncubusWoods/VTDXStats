package dev.incubuswoods.vtdxstats;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

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
            case "admin_config" -> handleAdminConfig(p, (Guis.AdminConfigHolder) h, slot, e.isRightClick());
        }
    }

    private void handleOverview(Player p, Guis.OverviewHolder h, int slot) {
        StatConfig c = plugin.cfg();
        if (slot == c.slot("gui.overview.slots.repartir", 49)) plugin.guis().openRepartir(p, h.owner);
        if (slot == c.slot("gui.overview.slots.cerrar", 53)) p.closeInventory();
    }

    private void handleRepartir(Player p, Guis.RepartirHolder h, int slot, boolean left, boolean right, boolean shift) {
        StatConfig c = plugin.cfg();
        if (slot == c.slot("gui.repartir.slots.volver", 45)) { plugin.guis().openOverview(p, h.owner); return; }
        if (slot == c.slot("gui.repartir.slots.cerrar", 53)) { p.closeInventory(); return; }
        String stat = h.statSlots.get(slot);
        if (stat == null) return;
        StatDef def = c.get(stat); if (def == null) return;
        int pts = plugin.stats().getPoints(h.owner, stat);
        int cap = plugin.stats().effectiveMaxPoints(h.owner, def);
        if (pts >= cap) {
            p.sendMessage(GuiUtil.mm(pts >= def.maxPoints() ? plugin.msg("messages.maxed") : plugin.msg("messages.level-cap")));
            return;
        }
        int want = shift ? (cap - pts) : (right ? 5 : 1);
        int added = plugin.stats().addPoints(h.owner, stat, want);
        if (added <= 0) { p.sendMessage(GuiUtil.mm(plugin.msg("messages.no-points"))); return; }
        p.sendMessage(GuiUtil.mm(plugin.msg("messages.assigned").replace("{amount}", String.valueOf(added)).replace("{stat}", def.name())));
        plugin.guis().openRepartir(p, h.owner);
    }

    private void handleAdminPlayers(Player p, Guis.AdminPlayersHolder h, int slot) {
        StatConfig c = plugin.cfg();
        java.util.UUID target = h.targets.get(slot);
        if (target != null) { plugin.guis().openAdminPlayer(p, target, h.page); return; }
        if (slot == c.slot("gui.admin.slots.prev",45) && h.page>0) plugin.guis().openAdminPlayers(p, h.page-1);
        if (slot == c.slot("gui.admin.slots.next",53)) plugin.guis().openAdminPlayers(p, h.page+1);
        if (slot == c.slot("gui.admin.slots.close",49)) p.closeInventory();
    }

    private void handleAdminPlayer(Player p, Guis.AdminPlayerHolder h, int slot) {
        StatConfig c = plugin.cfg();
        if (slot == c.slot("gui.admin.slots.back",45)) { plugin.guis().openAdminPlayers(p, h.page); return; }
        if (slot == c.slot("gui.admin.slots.close",53)) { p.closeInventory(); return; }
        if (slot == c.slot("gui.admin.slots.config",46)) { plugin.guis().openAdminConfig(p, h.target); return; }
        if (slot == c.slot("gui.admin.slots.reset",49)) {
            plugin.stats().reset(h.target);
            String name = java.util.Optional.ofNullable(org.bukkit.Bukkit.getOfflinePlayer(h.target).getName()).orElse("Jugador");
            p.sendMessage(GuiUtil.mm(plugin.msg("messages.reset-other").replace("{player}", name)));
            plugin.guis().openAdminPlayer(p, h.target, h.page);
            return;
        }
        Guis.ScaleRef ref = h.scaleSlots.get(slot);
        if (ref == null) return;
        boolean nowDisabled = plugin.stats().toggleScaleDisabled(h.target, StatService.scaleKey(ref.stat(), ref.subType(), ref.id()));
        String name = java.util.Optional.ofNullable(org.bukkit.Bukkit.getOfflinePlayer(h.target).getName()).orElse("Jugador");
        p.sendMessage(GuiUtil.mm((nowDisabled?"<red>Desactivado ":"<green>Activado ") + "<white>"+ref.display()+"</white> <gray>para "+name));
        plugin.guis().openAdminPlayer(p, h.target, h.page);
    }

    private void handleAdminConfig(Player p, Guis.AdminConfigHolder h, int slot, boolean rightClick) {
        if (slot == 45) { plugin.guis().openAdminPlayer(p, h.target, 0); return; }
        if (slot == 53) { p.closeInventory(); return; }
        Guis.EditRef ref = h.edits.get(slot);
        if (ref == null) return;
        String type = rightClick ? "MODEL" : "SLOT";
        plugin.pendingEdits.put(p.getUniqueId(), new VTDXStats.PendingEdit(type, ref.stat(), ref.subType(), ref.id()));
        p.closeInventory();
        if (rightClick) p.sendMessage(GuiUtil.mm("<yellow>Escribe el item model para <white>"+ref.display()+"</white> <gray>(ej: vtuberdex:armas/orbe_vtuber, o 'clear')</gray>. Escribe 'cancel' para cancelar."));
        else p.sendMessage(GuiUtil.mm("<yellow>Escribe el slot (0-53) para <white>"+ref.display()+"</white>. Escribe 'cancel' para cancelar."));
    }
}
