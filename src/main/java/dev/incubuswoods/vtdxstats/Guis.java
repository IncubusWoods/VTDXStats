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
    private final Map<UUID, UUID> adminTarget = new HashMap<>();
    Guis(VTDXStats plugin) { this.plugin = plugin; }
    UUID lastAdminTarget(UUID viewer) { return adminTarget.getOrDefault(viewer, viewer); }

    interface VtdxHolder extends InventoryHolder { String type(); }
    static final class OverviewHolder implements VtdxHolder { final UUID owner; final Inventory inv; OverviewHolder(UUID owner, int size, Component title){this.owner=owner;this.inv=Bukkit.createInventory(this,size,title);} public Inventory getInventory(){return inv;} public String type(){return "overview";} }
    static final class RepartirHolder implements VtdxHolder { final UUID owner; final Inventory inv; final Map<Integer,String> statSlots=new HashMap<>(); RepartirHolder(UUID owner,int size,Component title){this.owner=owner;this.inv=Bukkit.createInventory(this,size,title);} public Inventory getInventory(){return inv;} public String type(){return "repartir";} }
    static final class AdminPlayersHolder implements VtdxHolder { final int page; final Inventory inv; final Map<Integer,UUID> targets=new HashMap<>(); AdminPlayersHolder(int page,int size,Component title){this.page=page;this.inv=Bukkit.createInventory(this,size,title);} public Inventory getInventory(){return inv;} public String type(){return "admin_players";} }
    static final class AdminPlayerHolder implements VtdxHolder { final UUID target; final int page; final Inventory inv; final Map<Integer,ScaleRef> scaleSlots=new HashMap<>(); AdminPlayerHolder(UUID target,int page,int size,Component title){this.target=target;this.page=page;this.inv=Bukkit.createInventory(this,size,title);} public Inventory getInventory(){return inv;} public String type(){return "admin_player";} }
    static final class AdminConfigHolder implements VtdxHolder { final UUID target; final Inventory inv; final Map<Integer,EditRef> edits=new HashMap<>(); AdminConfigHolder(UUID target,int size,Component title){this.target=target;this.inv=Bukkit.createInventory(this,size,title);} public Inventory getInventory(){return inv;} public String type(){return "admin_config";} }
    record ScaleRef(String stat, String subType, String id, String display) {}
    record EditRef(String stat, String subType, String id, String display) {}

    void openOverview(Player viewer, UUID owner) {
        StatConfig c = plugin.cfg();
        int size = sz(c, "gui.overview.size", 54);
        OverviewHolder h = new OverviewHolder(owner, size, mm(c.guiTitle("gui.overview.title","<dark_purple>Tus stats</dark_purple>")));
        fill(h.inv, c.guiFiller("gui.overview.filler", Material.GRAY_STAINED_GLASS_PANE));
        int level = plugin.stats().resolveLevel(owner); int total = plugin.stats().totalPoints(owner); int avail = plugin.stats().availablePoints(owner);
        put(h.inv, c.slot("gui.overview.slots.info",4), size, item(Material.BOOK,"<gold>Stats</gold>", List.of("<gray>Nivel: <white>"+level+"</white>","<gray>Puntos totales: <white>"+total+"</white>","<gray>Disponibles: <green>"+avail+"</green>","<dark_gray>Click en repartir para asignar</dark_gray>")));
        List<Integer> vSlots = c.slotList("gui.overview.slots.values", List.of(9,10,12,13,15,16,27,28,30,31,33,34));
        int vi=0; Set<Integer> usedSlots = new HashSet<>();
        for (StatDef def : c.defs()) {
            int pts = plugin.stats().getPoints(owner, def.key());
            for (StatAttribute a : def.attributes()) {
                int slot = a.slot() >= 0 ? a.slot() : (vi < vSlots.size() ? vSlots.get(vi) : -1);
                vi++;
                slot = clampSlot(slot, size); if (slot<0 || !usedSlots.add(slot)) continue;
                boolean disabled = plugin.stats().isScaleDisabled(owner, StatService.scaleKey(def.key(),"attr",a.id()));
                double val = disabled ? 0 : a.valueFor(pts, def.maxPoints());
                List<String> lore = new ArrayList<>();
                lore.add("<gray>Valor: <green>"+plugin.stats().fmt(val)+"</green>");
                lore.add("<gray>Por punto: <white>"+plugin.stats().fmt(a.perPoint())+"</white> <gray>Cap: <white>"+plugin.stats().fmt(a.cap())+"</white>");
                lore.add("<gray>Stat: <aqua>"+def.name()+"</aqua> <white>"+pts+"/"+def.maxPoints()+"</white>");
                if (disabled) lore.add("<red>Desactivado por admin</red>");
                h.inv.setItem(slot, item(a.icon()!=null?a.icon():def.icon(), "<aqua>"+def.name()+" <gray>? <white>"+a.display()+"</white>", lore, a.itemModel()));
            }
        }
        List<Integer> bSlots = c.slotList("gui.overview.slots.bonuses", List.of(20,21,23,24,38,39));
        int bi=0;
        for (StatDef def : c.defs()) {
            int preferred = -1;
            for (StatBonus b : def.bonuses()) if (b.slot()>=0) { preferred = b.slot(); break; }
            int slot = preferred >= 0 ? preferred : (bi < bSlots.size() ? bSlots.get(bi) : -1);
            bi++;
            slot = clampSlot(slot, size); if (slot<0 || !usedSlots.add(slot)) continue;
            int pts = plugin.stats().getPoints(owner, def.key()); boolean max = pts >= def.maxPoints();
            boolean anyDisabled = false, anyActive = false;
            List<String> lore = new ArrayList<>(); lore.add("<gray>Puntos: <white>"+pts+"/"+def.maxPoints()+"</white>");
            for (StatBonus b : def.bonuses()) {
                boolean dis = plugin.stats().isScaleDisabled(owner, StatService.scaleKey(def.key(),"bonus",b.id()));
                if (dis) anyDisabled = true; if (max && !dis) anyActive = true;
                lore.add((dis?"<red>? ":"<light_purple>? ")+b.display()+(dis?" <dark_gray>(desactivado)</dark_gray>":"</light_purple>"));
            }
            lore.add(anyActive ? "<green>? Bono activo</green>" : (anyDisabled ? "<red>Hay bonos desactivados</red>" : "<dark_gray>No activo</dark_gray>"));
            Material mat = anyActive ? Material.NETHER_STAR : Material.COAL;
            String model = def.bonuses().isEmpty() ? def.itemModel() : def.bonuses().get(0).itemModel();
            h.inv.setItem(slot, item(mat, (anyActive?"<gold>":"<gray>")+"Bono: "+def.name(), lore, model));
        }
        put(h.inv, c.slot("gui.overview.slots.repartir",49), size, item(avail>0?Material.EXPERIENCE_BOTTLE:Material.GRAY_DYE, avail>0?"<green>Repartir puntos ("+avail+")":"<gray>Sin puntos por repartir", List.of("<gray>Click para abrir reparto</gray>")));
        put(h.inv, c.slot("gui.overview.slots.cerrar",53), size, item(Material.BARRIER,"<red>Cerrar</red>", List.of()));
        viewer.openInventory(h.inv);
    }

    void openRepartir(Player viewer, UUID owner) {
        StatConfig c = plugin.cfg(); int size = sz(c, "gui.repartir.size",54);
        RepartirHolder h = new RepartirHolder(owner,size,mm(c.guiTitle("gui.repartir.title","<dark_purple>Reparte tus puntos</dark_purple>")));
        fill(h.inv, c.guiFiller("gui.repartir.filler", Material.GRAY_STAINED_GLASS_PANE));
        int avail = plugin.stats().availablePoints(owner);
        put(h.inv, c.slot("gui.repartir.slots.info",4), size, item(Material.EXPERIENCE_BOTTLE, "<gold>Puntos disponibles: <green>"+avail+"</green>", List.of("<gray>Click izq: +1</gray>","<gray>Click der: +5</gray>","<gray>Shift+click: m?ximo posible</gray>")));
        List<Integer> slots = slotListForStats(c, size);
        int i=0; for (StatDef def : c.defs()) { if (i>=slots.size()) break; int slot = clampSlot(slots.get(i++), size); if (slot<0) continue; h.statSlots.put(slot, def.key()); h.inv.setItem(slot, statAssignItem(owner, def, avail)); }
        put(h.inv, c.slot("gui.repartir.slots.volver",45), size, item(Material.ARROW,"<yellow>Volver</yellow>", List.of()));
        put(h.inv, c.slot("gui.repartir.slots.cerrar",53), size, item(Material.BARRIER,"<red>Cerrar</red>", List.of()));
        viewer.openInventory(h.inv);
    }

    ItemStack statAssignItem(UUID u, StatDef def, int avail) {
        int pts = plugin.stats().getPoints(u, def.key()); int max = def.maxPoints(); int cap = plugin.stats().effectiveMaxPoints(u, def);
        int bars = (int)Math.max(0, Math.min(10, Math.round(10.0 * pts / Math.max(1, max))));
        String bar = "<green>"+"?".repeat(bars)+"</green><dark_gray>"+"?".repeat(10-bars)+"</dark_gray>";
        List<String> lore = new ArrayList<>(); lore.add(bar); lore.add("<gray>Puestos: <white>"+pts+" / "+max+"</white>");
        if (cap < max) lore.add("<gray>L?mite por nivel: <yellow>"+cap+"</yellow> <dark_gray>(sube de nivel para m?s)</dark_gray>");
        for (StatAttribute a : def.attributes()) {
            boolean dis = plugin.stats().isScaleDisabled(u, StatService.scaleKey(def.key(),"attr",a.id()));
            double val = dis ? 0 : a.valueFor(pts, max);
            lore.add((dis?"<red>? ":"<gray>")+a.display()+": <green>"+plugin.stats().fmt(val)+"</green>"+(dis?" <dark_gray>(desactivado)</dark_gray>":""));
        }
        if (pts >= max) lore.add("<gold>M?ximo alcanzado</gold>");
        else if (pts >= cap) lore.add("<yellow>L?mite por nivel alcanzado: "+cap+"/"+max+"</yellow>");
        else if (avail <= 0) lore.add("<gray>Sin puntos disponibles</gray>");
        else lore.add("<yellow>Izq: +1 | Der: +5 | Shift: m?x</yellow>");
        return item(def.icon(), "<aqua>"+def.name(), lore, def.itemModel());
    }

    void openAdminPlayers(Player viewer, int page) {
        StatConfig c = plugin.cfg(); int size = sz(c, "gui.admin.size",54);
        AdminPlayersHolder h = new AdminPlayersHolder(Math.max(0,page), size, mm(c.guiTitle("gui.admin.players-title","<dark_red>VTDXStats Admin</dark_red>")));
        fill(h.inv, c.guiFiller("gui.admin.filler", c.guiFiller("gui.overview.filler", Material.GRAY_STAINED_GLASS_PANE)));
        List<Integer> slots = c.slotList("gui.admin.players-slots", c.slotList("gui.admin.slots.players", defSlots(size)));
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers()); online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int per = Math.max(1, slots.size()); int start = h.page*per;
        for (int i=0; i<per && start+i<online.size(); i++) {
            Player t = online.get(start+i); int slot = clampSlot(slots.get(i), size); if (slot<0) continue; h.targets.put(slot, t.getUniqueId());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD); SkullMeta sm = (SkullMeta) head.getItemMeta();
            if (sm!=null){ sm.setOwningPlayer(t); sm.displayName(mm("<yellow>"+t.getName())); sm.lore(List.of(mm("<gray>Nivel: <white>"+plugin.stats().resolveLevel(t.getUniqueId())), mm("<gray>Disponibles: <green>"+plugin.stats().availablePoints(t.getUniqueId())), mm("<aqua>Click para gestionar"))); head.setItemMeta(sm); }
            h.inv.setItem(slot, head);
        }
        if (h.page>0) put(h.inv, c.slot("gui.admin.slots.prev",45), size, item(Material.ARROW,"<yellow>Anterior</yellow>", List.of()));
        if (start+per < online.size()) put(h.inv, c.slot("gui.admin.slots.next",53), size, item(Material.ARROW,"<yellow>Siguiente</yellow>", List.of()));
        put(h.inv, c.slot("gui.admin.slots.close",49), size, item(Material.BARRIER,"<red>Cerrar</red>", List.of()));
        viewer.openInventory(h.inv);
    }

    void openAdminPlayer(Player viewer, UUID target, int page) {
        adminTarget.put(viewer.getUniqueId(), target);
        StatConfig c = plugin.cfg(); int size = sz(c, "gui.admin.size",54);
        String name = Optional.ofNullable(Bukkit.getOfflinePlayer(target).getName()).orElse(target.toString().substring(0,8));
        String title = c.guiTitle("gui.admin.player-title","<dark_red>Stats: {player}</dark_red>").replace("{player}", name);
        AdminPlayerHolder h = new AdminPlayerHolder(target,page,size,mm(title));
        fill(h.inv, c.guiFiller("gui.admin.filler", c.guiFiller("gui.overview.filler", Material.GRAY_STAINED_GLASS_PANE)));
        put(h.inv, c.slot("gui.admin.slots.info",4), size, item(Material.BOOK,"<gold>"+name+"</gold>", List.of("<gray>Nivel: <white>"+plugin.stats().resolveLevel(target),"<gray>Disponibles: <green>"+plugin.stats().availablePoints(target),"<yellow>Click en un atributo/bono para activar/desactivar</yellow>")));
        List<Integer> slots = c.slotList("gui.admin.slots.stats", List.of(19,20,21,22,23,24,28,29,30,31,32,33,37,38,39,40,41,42));
        int i=0;
        for (StatDef def : c.defs()) {
            for (StatAttribute a : def.attributes()) {
                if (i>=slots.size()) break; int slot=clampSlot(slots.get(i++), size); if (slot<0) continue;
                boolean dis = plugin.stats().isScaleDisabled(target, StatService.scaleKey(def.key(),"attr",a.id()));
                int pts = plugin.stats().getPoints(target, def.key());
                h.scaleSlots.put(slot, new ScaleRef(def.key(), "attr", a.id(), a.display()));
                List<String> lore = new ArrayList<>(); lore.add("<gray>Stat: <aqua>"+def.name()); lore.add("<gray>Puntos: <white>"+pts+"/"+def.maxPoints()); lore.add("<gray>Valor actual: <green>"+plugin.stats().fmt(dis?0:a.valueFor(pts, def.maxPoints())));
                lore.add(dis?"<red>ESCALADO DESACTIVADO</red>":"<green>Escalado activo</green>"); lore.add("<yellow>Click para alternar</yellow>");
                h.inv.setItem(slot, item(dis?Material.BARRIER:(a.icon()!=null?a.icon():def.icon()), (dis?"<red>":"<aqua>")+def.name()+" <gray>? "+a.display(), lore, a.itemModel()));
            }
            for (StatBonus b : def.bonuses()) {
                if (i>=slots.size()) break; int slot=clampSlot(slots.get(i++), size); if (slot<0) continue;
                boolean dis = plugin.stats().isScaleDisabled(target, StatService.scaleKey(def.key(),"bonus",b.id()));
                int pts = plugin.stats().getPoints(target, def.key());
                h.scaleSlots.put(slot, new ScaleRef(def.key(), "bonus", b.id(), b.display()));
                List<String> lore = new ArrayList<>(); lore.add("<gray>Stat: <aqua>"+def.name()); lore.add("<gray>Puntos: <white>"+pts+"/"+def.maxPoints()); lore.add(dis?"<red>BONO DESACTIVADO</red>":"<green>Bono activo al maxear</green>"); lore.add("<yellow>Click para alternar</yellow>");
                h.inv.setItem(slot, item(dis?Material.BARRIER:(b.icon()!=null?b.icon():Material.NETHER_STAR), (dis?"<red>":"<gold>")+"Bono: "+def.name(), lore, b.itemModel()));
            }
        }
        put(h.inv, c.slot("gui.admin.slots.reset",49), size, item(Material.TNT,"<red>Resetear distribuci?n</red>", List.of("<gray>Devuelve todos los puntos asignados</gray>")));
        put(h.inv, c.slot("gui.admin.slots.back",45), size, item(Material.ARROW,"<yellow>Volver</yellow>", List.of()));
        put(h.inv, c.slot("gui.admin.slots.close",53), size, item(Material.BARRIER,"<red>Cerrar</red>", List.of()));
        put(h.inv, c.slot("gui.admin.slots.config",46), size, item(Material.COMPARATOR,"<gold>Configurar slots/modelos</gold>", List.of("<gray>Click izq: cambiar slot</gray>","<gray>Click der: cambiar item model</gray>")));
        viewer.openInventory(h.inv);
    }

    void openAdminConfig(Player viewer, UUID target) {
        adminTarget.put(viewer.getUniqueId(), target);
        StatConfig c = plugin.cfg(); int size=54;
        String name = Optional.ofNullable(Bukkit.getOfflinePlayer(target).getName()).orElse("Jugador");
        AdminConfigHolder h = new AdminConfigHolder(target,size,mm("<dark_red>Config GUI: "+name+"</dark_red>"));
        fill(h.inv, c.guiFiller("gui.admin.filler", Material.GRAY_STAINED_GLASS_PANE));
        int slotIdx=0;
        for (StatDef def : c.defs()) {
            // stat slot config item
            int ss = slotForStat(c, def.key(), size, slotIdx);
            if (ss>=0 && ss<size) {
                h.edits.put(ss, new EditRef(def.key(),"stat","stat","Stat "+def.name()));
                h.inv.setItem(ss, item(def.icon(), "<aqua>Stat: "+def.name(), List.of("<gray>Slot reparto: <white>"+ss, "<gray>Item model: <white>"+blank(def.itemModel()), "<yellow>Click izq: escribir slot en chat</yellow>", "<yellow>Click der: escribir item model</yellow>", "<dark_gray>'cancel' para cancelar</dark_gray>"), def.itemModel()));
            }
            slotIdx++;
        }
        // attributes/bonuses in subsequent rows
        int i=18;
        for (StatDef def : c.defs()) {
            for (StatAttribute a : def.attributes()) {
                if (i>=45) break; int sSlot = i++;
                h.edits.put(sSlot, new EditRef(def.key(),"attr",a.id(),a.display()));
                h.inv.setItem(sSlot, item(a.icon()!=null?a.icon():def.icon(), "<yellow>"+def.name()+" <gray>? <white>"+a.display(), List.of("<gray>ID: <white>"+a.id(), "<gray>Slot GUI: <white>"+(a.slot()<0?"auto":a.slot()), "<gray>Item model: <white>"+blank(a.itemModel()), "<yellow>Click izq: set slot</yellow>","<yellow>Click der: set item model</yellow>"), a.itemModel()));
            }
            for (StatBonus b : def.bonuses()) {
                if (i>=45) break; int sSlot=i++;
                h.edits.put(sSlot, new EditRef(def.key(),"bonus",b.id(),b.display()));
                h.inv.setItem(sSlot, item(b.icon()!=null?b.icon():Material.NETHER_STAR, "<gold>"+def.name()+" <gray>? <white>"+b.display(), List.of("<gray>ID: <white>"+b.id(), "<gray>Slot GUI: <white>"+(b.slot()<0?"auto":b.slot()), "<gray>Item model: <white>"+blank(b.itemModel()), "<yellow>Click izq: set slot</yellow>","<yellow>Click der: set item model</yellow>"), b.itemModel()));
            }
        }
        put(h.inv,45,size,item(Material.ARROW,"<yellow>Volver</yellow>", List.of()));
        put(h.inv,53,size,item(Material.BARRIER,"<red>Cerrar</red>", List.of()));
        viewer.openInventory(h.inv);
    }

    private List<Integer> slotListForStats(StatConfig c, int size) {
        // prefer gui.repartir.stat-slots.<key>, fallback to gui.repartir.slots.stats list
        List<String> defs = c.statOrder;
        Map<String,Integer> byKey = new HashMap<>();
        for (String k : defs) {
            int v = plugin.getConfig().getInt("gui.repartir.stat-slots."+k.toLowerCase(Locale.ROOT), -1);
            if (v>=0 && v<size) byKey.put(k.toLowerCase(Locale.ROOT), v);
        }
        List<Integer> fallback = c.slotList("gui.repartir.slots.stats", List.of(19,21,23,25,29,31));
        List<Integer> out = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        int fi=0;
        for (String k : defs) {
            Integer v = byKey.get(k.toLowerCase(Locale.ROOT));
            if (v!=null && used.add(v)) { out.add(v); continue; }
            while (fi < fallback.size()) { int fv = fallback.get(fi++); if (fv>=0 && fv<size && used.add(fv)) { out.add(fv); break; } }
            if (out.size() < defs.indexOf(k)+1) { for (int s=0;s<size;s++) if (used.add(s)) { out.add(s); break; } }
        }
        return out;
    }

    private static int slotForStat(StatConfig c, String key, int size, int idx) {
        int v = c.plugin.getConfig().getInt("gui.repartir.stat-slots."+key.toLowerCase(Locale.ROOT), -1);
        if (v>=0 && v<size) return v;
        List<Integer> fb = c.slotList("gui.repartir.slots.stats", List.of(10,12,14,16,28,30));
        return idx < fb.size() ? fb.get(idx) : idx;
    }

    private static List<Integer> defSlots(int size){ List<Integer> l=new ArrayList<>(); for(int i=10;i<size-9;i++){ if(i%9==0||i%9==8) continue; l.add(i);} return l; }
    private static int sz(StatConfig c, String path, int def){ int v=c.slot(path,def); if (v==def) v=c.plugin.getConfig().getInt(path,def); v=Math.max(9,Math.min(54,(v/9)*9)); return v==0?54:v; }
    private static void put(Inventory inv, int slot, int size, ItemStack it){ int s=clampSlot(slot,size); if (s>=0) inv.setItem(s,it); }
    private static String blank(String s){ return s==null||s.isBlank()?"<dark_gray>ninguno</dark_gray>":"<white>"+s; }
}
