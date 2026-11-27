package dev.incubuswoods.vtdxstats;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

import static dev.incubuswoods.vtdxstats.GuiUtil.*;

final class Guis {
    private final VTDXStats plugin;
    Guis(VTDXStats plugin) { this.plugin = plugin; }

    interface VtdxHolder extends InventoryHolder { String type(); }

    static final class OverviewHolder implements VtdxHolder { final UUID owner; final Inventory inv;
        OverviewHolder(UUID owner, int size, Component title) { this.owner = owner; this.inv = Bukkit.createInventory(this, size, title); }
        public Inventory getInventory() { return inv; } public String type() { return "overview"; } }
    static final class RepartirHolder implements VtdxHolder { final UUID owner; final Inventory inv; final Map<Integer,String> statSlots = new HashMap<>();
        RepartirHolder(UUID owner, int size, Component title) { this.owner = owner; this.inv = Bukkit.createInventory(this, size, title); }
        public Inventory getInventory() { return inv; } public String type() { return "repartir"; } }
    static final class AdminPlayersHolder implements VtdxHolder { final int page; final Inventory inv; final Map<Integer,UUID> targets = new HashMap<>();
        AdminPlayersHolder(int page, int size, Component title) { this.page = page; this.inv = Bukkit.createInventory(this, size, title); }
        public Inventory getInventory() { return inv; } public String type() { return "admin_players"; } }
    static final class AdminPlayerHolder implements VtdxHolder { final UUID target; final int page; final Inventory inv; final Map<Integer,String> statSlots = new HashMap<>();
        AdminPlayerHolder(UUID target, int page, int size, Component title) { this.target = target; this.page = page; this.inv = Bukkit.createInventory(this, size, title); }
        public Inventory getInventory() { return inv; } public String type() { return "admin_player"; } }

    void openOverview(Player viewer, UUID owner) {
        StatConfig c = plugin.cfg();
        int size = Math.min(54, Math.max(9, (c.slot("gui.overview.size",54)/9)*9));
        OverviewHolder h = new OverviewHolder(owner, size, mm(c.guiTitle("gui.overview.title","<dark_purple>Tus stats</dark_purple>")));
        fill(h.inv, c.guiFiller("gui.overview.filler", Material.GRAY_STAINED_GLASS_PANE));
        UUID u = owner;
        int level = plugin.stats().resolveLevel(u);
        int total = plugin.stats().totalPoints(u);
        int avail = plugin.stats().availablePoints(u);
        int infoSlot = clampSlot(c.slot("gui.overview.slots.info",4), size);
        if (infoSlot>=0) h.inv.setItem(infoSlot, item(Material.BOOK, "<gold>Stats</gold>", List.of(
                "<gray>Nivel: <white>"+level+"</white>",
                "<gray>Puntos totales: <white>"+total+"</white>",
                "<gray>Disponibles: <green>"+avail+"</green>",
                "<dark_gray>Click en repartir para asignar</dark_gray>")));

        List<Integer> vSlots = c.slotList("gui.overview.slots.values", List.of(9,10,12,13,15,16,18,19,21,22,24,25));
        int vi = 0;
        for (StatDef def : c.defs()) {
            int pts = plugin.stats().getPoints(u, def.key());
            boolean disabled = plugin.stats().isStatDisabled(u, def.key());
            for (StatAttribute a : def.attributes()) {
                if (vi >= vSlots.size()) break;
                int slot = clampSlot(vSlots.get(vi++), size); if (slot < 0) continue;
                double val = disabled ? 0 : a.valueFor(pts, def.maxPoints());
                List<String> lore = new ArrayList<>();
                lore.add("<gray>Valor: <green>"+plugin.stats().fmt(val)+"</green>");
                lore.add("<gray>Por punto: <white>"+plugin.stats().fmt(a.perPoint())+"</white> <gray>Cap: <white>"+plugin.stats().fmt(a.cap())+"</white>");
                lore.add("<gray>Stat: <aqua>"+def.name()+"</aqua> <white>"+pts+"/"+def.maxPoints()+"</white>");
                if (disabled) lore.add("<red>Desactivado por admin</red>");
                h.inv.setItem(slot, item(def.icon(), "<aqua>"+def.name()+" <gray>? <white>"+a.display(), lore));
            }
        }
        List<Integer> bSlots = c.slotList("gui.overview.slots.bonuses", List.of(28,31,34,37,40,43));
        int bi = 0;
        for (StatDef def : c.defs()) {
            if (bi >= bSlots.size()) break;
            int slot = clampSlot(bSlots.get(bi++), size); if (slot < 0) continue;
            int pts = plugin.stats().getPoints(u, def.key());
            boolean disabled = plugin.stats().isStatDisabled(u, def.key());
            boolean max = pts >= def.maxPoints();
            boolean active = max && !disabled;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Puntos: <white>"+pts+"/"+def.maxPoints()+"</white>");
            for (StatBonus b : def.bonuses()) lore.add("<light_purple>"+b.display()+"</light_purple>");
            lore.add(active ? "<green>? Activo</green>" : (disabled ? "<red>Desactivado por admin</red>" : "<dark_gray>No activo</dark_gray>"));
            h.inv.setItem(slot, item(active ? Material.NETHER_STAR : Material.COAL, (active?"<gold>":"<gray>")+"Bono ? "+def.name(), lore));
        }
        int repSlot = clampSlot(c.slot("gui.overview.slots.repartir",49), size);
        if (repSlot>=0) h.inv.setItem(repSlot, item(avail>0?Material.EXPERIENCE_BOTTLE:Material.GRAY_DYE, avail>0?"<green>Repartir puntos ("+avail+")":"<gray>Sin puntos por repartir", List.of("<gray>Click para abrir reparto</gray>")));
        int close = clampSlot(c.slot("gui.overview.slots.cerrar",53), size);
        if (close>=0) h.inv.setItem(close, item(Material.BARRIER, "<red>Cerrar</red>", List.of()));
        viewer.openInventory(h.inv);
    }

    void openRepartir(Player viewer, UUID owner) {
        StatConfig c = plugin.cfg();
        int size = Math.min(54, Math.max(9, (c.slot("gui.repartir.size",54)/9)*9));
        RepartirHolder h = new RepartirHolder(owner, size, mm(c.guiTitle("gui.repartir.title","<dark_purple>Reparte tus puntos</dark_purple>")));
        fill(h.inv, c.guiFiller("gui.repartir.filler", Material.GRAY_STAINED_GLASS_PANE));
        UUID u = owner;
        int avail = plugin.stats().availablePoints(u);
        int info = clampSlot(c.slot("gui.repartir.slots.info",22), size);
        if (info>=0) h.inv.setItem(info, item(Material.EXPERIENCE_BOTTLE, "<gold>Puntos disponibles: <green>"+avail+"</green>", List.of("<gray>Click izq: +1</gray>","<gray>Click der: +5</gray>","<gray>Shift+click: m?ximo posible</gray>")));
        List<Integer> slots = c.slotList("gui.repartir.slots.stats", List.of(4,15,33,40,29,11));
        int i=0;
        for (StatDef def : c.defs()) {
            if (i >= slots.size()) break;
            int slot = clampSlot(slots.get(i++), size); if (slot<0) continue;
            h.statSlots.put(slot, def.key());
            h.inv.setItem(slot, statAssignItem(u, def, avail));
        }
        int close = clampSlot(c.slot("gui.repartir.slots.cerrar",49), size);
        if (close>=0) h.inv.setItem(close, item(Material.BARRIER, "<red>Cerrar</red>", List.of()));
        viewer.openInventory(h.inv);
    }

    ItemStack statAssignItem(UUID u, StatDef def, int avail) {
        int pts = plugin.stats().getPoints(u, def.key());
        boolean disabled = plugin.stats().isStatDisabled(u, def.key());
        int max = def.maxPoints();
        int bars = (int)Math.max(0, Math.min(10, Math.round(10.0 * pts / Math.max(1, max))));
        String bar = "<aqua>"+"?".repeat(bars)+"</aqua><dark_gray>"+"?".repeat(10-bars)+"</dark_gray>";
        List<String> lore = new ArrayList<>();
        lore.add(bar);
        lore.add("<gray>Puestos: <white>"+pts+" / "+max+"</white>");
        for (StatAttribute a : def.attributes()) {
            double val = disabled ? 0 : a.valueFor(pts, max);
            lore.add("<gray>"+a.display()+": <green>"+plugin.stats().fmt(val)+"</green>");
        }
        if (disabled) lore.add("<red>Desactivada por admin</red>");
        else if (pts >= max) lore.add("<gold>M?ximo alcanzado</gold>");
        else if (avail <= 0) lore.add("<gray>Sin puntos disponibles</gray>");
        else lore.add("<yellow>Izq: +1 | Der: +5 | Shift: m?x</yellow>");
        return item(def.icon(), "<aqua>"+def.name(), lore);
    }

    void openAdminPlayers(Player viewer, int page) {
        StatConfig c = plugin.cfg();
        int size = Math.min(54, Math.max(9, (c.slot("gui.admin-players.size",54)/9)*9));
        AdminPlayersHolder h = new AdminPlayersHolder(Math.max(0,page), size, mm(c.guiTitle("gui.admin-players.title","<dark_red>VTDXStats - Jugadores</dark_red>")));
        fill(h.inv, c.guiFiller("gui.admin-players.filler", Material.GRAY_STAINED_GLASS_PANE));
        List<Integer> slots = c.slotList("gui.admin-players.slots.players", List.of(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34));
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int perPage = slots.size();
        int start = h.page * perPage;
        for (int i=0; i<perPage && start+i < online.size(); i++) {
            Player p = online.get(start+i);
            int slot = clampSlot(slots.get(i), size); if (slot<0) continue;
            h.targets.put(slot, p.getUniqueId());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            if (sm != null) { sm.setOwningPlayer(p); sm.displayName(mm("<yellow>"+p.getName()+"</yellow>")); sm.lore(List.of(mm("<gray>Click para gestionar stats</gray>"))); head.setItemMeta(sm); }
            h.inv.setItem(slot, head);
        }
        int prev = clampSlot(c.slot("gui.admin-players.slots.previous",45), size);
        int next = clampSlot(c.slot("gui.admin-players.slots.next",53), size);
        int close = clampSlot(c.slot("gui.admin-players.slots.close",49), size);
        if (prev>=0 && h.page>0) h.inv.setItem(prev, item(Material.ARROW,"<yellow>P?gina anterior</yellow>",List.of()));
        if (next>=0 && start+perPage < online.size()) h.inv.setItem(next, item(Material.ARROW,"<yellow>P?gina siguiente</yellow>",List.of()));
        if (close>=0) h.inv.setItem(close, item(Material.BARRIER,"<red>Cerrar</red>",List.of()));
        viewer.openInventory(h.inv);
    }

    void openAdminPlayer(Player viewer, UUID target, int page) {
        StatConfig c = plugin.cfg();
        int size = Math.min(54, Math.max(9, (c.slot("gui.admin-player.size",54)/9)*9));
        String name = Optional.ofNullable(Bukkit.getOfflinePlayer(target).getName()).orElse(target.toString().substring(0,8));
        String title = c.guiTitle("gui.admin-player.title","<dark_red>Stats de {player}</dark_red>").replace("{player}", name);
        AdminPlayerHolder h = new AdminPlayerHolder(target, page, size, mm(title));
        fill(h.inv, c.guiFiller("gui.admin-player.filler", Material.GRAY_STAINED_GLASS_PANE));
        int info = clampSlot(c.slot("gui.admin-player.slots.info",4), size);
        int avail = plugin.stats().availablePoints(target);
        if (info>=0) h.inv.setItem(info, item(Material.BOOK, "<gold>"+name+"</gold>", List.of("<gray>Nivel: <white>"+plugin.stats().resolveLevel(target)+"</white>","<gray>Disponibles: <green>"+avail+"</green>","<gray>Click en stat: activar/desactivar escalado</gray>")));
        List<Integer> slots = c.slotList("gui.admin-player.slots.stats", List.of(19,20,21,22,23,24));
        int i=0;
        for (StatDef def : c.defs()) {
            if (i>=slots.size()) break;
            int slot = clampSlot(slots.get(i++), size); if (slot<0) continue;
            h.statSlots.put(slot, def.key());
            boolean disabled = plugin.stats().isStatDisabled(target, def.key());
            int pts = plugin.stats().getPoints(target, def.key());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Puntos: <white>"+pts+"/"+def.maxPoints()+"</white>");
            lore.add(disabled ? "<red>ESCALADO DESACTIVADO</red>" : "<green>Escalado activo</green>");
            lore.add("<yellow>Click para alternar</yellow>");
            h.inv.setItem(slot, item(disabled?Material.BARRIER:def.icon(), (disabled?"<red>":"<aqua>")+def.name(), lore));
        }
        int reset = clampSlot(c.slot("gui.admin-player.slots.reset",49), size);
        int back = clampSlot(c.slot("gui.admin-player.slots.back",45), size);
        int close = clampSlot(c.slot("gui.admin-player.slots.close",53), size);
        if (reset>=0) h.inv.setItem(reset, item(Material.TNT, "<red>Resetear distribuci?n</red>", List.of("<gray>Devuelve todos los puntos asignados</gray>")));
        if (back>=0) h.inv.setItem(back, item(Material.ARROW, "<yellow>Volver</yellow>", List.of()));
        if (close>=0) h.inv.setItem(close, item(Material.BARRIER, "<red>Cerrar</red>", List.of()));
        viewer.openInventory(h.inv);
    }
}
