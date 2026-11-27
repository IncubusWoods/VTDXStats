package dev.incubuswoods.vtdxstats;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

final class StatConfig {
    final VTDXStats plugin;
    final Map<String, StatDef> stats = new LinkedHashMap<>();
    final List<String> statOrder = new ArrayList<>();
    FallbackSource fallbackSource = FallbackSource.LAST_KNOWN;
    int fallbackFixedLevel = 1;
    int maxLevel = 100;
    int pointsPerLevel = 5;
    boolean clampRefundOnReload = true;
    boolean mythicEnabled = true;
    String mythicKey = "vtdxstats";
    String mythicAdd = "ml stat add {player} {stat} {value} 0 {key}";
    String mythicRemove = "ml stat remove {player} {stat} {key}";
    int applyDelayTicks = 40;
    final java.util.List<int[]> statPointCaps = new java.util.ArrayList<>(); // [level, maxPoints]

    StatConfig(VTDXStats plugin) { this.plugin = plugin; reload(); }

    void reload() {
        FileConfiguration c = plugin.getConfig();
        fallbackSource = parseFallback(c.getString("level.fallback-source", "LAST_KNOWN"));
        fallbackFixedLevel = Math.max(1, c.getInt("level.fallback-fixed-level", 1));
        maxLevel = Math.max(1, c.getInt("level.max-level", 100));
        pointsPerLevel = Math.max(1, c.getInt("level.points-per-level", 5));
        clampRefundOnReload = c.getBoolean("clamp-refund-on-reload", true);
        mythicEnabled = c.getBoolean("mythiclib.enabled", true);
        mythicKey = c.getString("mythiclib.key", "vtdxstats");
        mythicAdd = c.getString("mythiclib.command-add", mythicAdd);
        mythicRemove = c.getString("mythiclib.command-remove", mythicRemove);
        applyDelayTicks = Math.max(1, c.getInt("mythiclib.apply-delay-ticks", 40));

        statPointCaps.clear();
        for (Map<?,?> m : c.getMapList("level.stat-point-caps")) {
            int lvl = (int) Math.max(1, num(m.get("level")));
            int mp = (int) Math.max(0, num(m.get("max-points")));
            statPointCaps.add(new int[]{lvl, mp});
        }
        statPointCaps.sort(java.util.Comparator.comparingInt(a -> a[0]));

        stats.clear(); statOrder.clear();
        ConfigurationSection sec = c.getConfigurationSection("stats");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            String name = s.getString("name", key);
            Material icon = Material.matchMaterial(s.getString("icon", "NETHER_STAR"));
            if (icon == null) icon = Material.NETHER_STAR;
            int maxPoints = Math.max(1, s.getInt("max-points", 200));
            String desc = s.getString("description", "");
            List<StatAttribute> attrs = new ArrayList<>();
            for (Map<?,?> m : s.getMapList("attributes")) {
                String id = str(m.get("id")); if (id.isBlank()) continue;
                double pp = num(m.get("per-point"));
                double cap = num(m.get("cap"));
                String display = str(m.get("display")); if (display.isBlank()) display = id;
                String im = str(m.get("item-model"));
                Material aicon = mat(str(m.get("icon")), icon);
                int aslot = (int) num(m.get("slot") == null ? -1 : m.get("slot"));
                attrs.add(new StatAttribute(id, pp, cap, display, aicon, im, aslot));
            }
            List<StatBonus> bonuses = new ArrayList<>();
            for (Map<?,?> m : s.getMapList("bonuses")) {
                String type = str(m.get("type")).toUpperCase(Locale.ROOT);
                String id = str(m.get("id")); if (id.isBlank()) id = type;
                String display = str(m.get("display")); if (display.isBlank()) display = id;
                String im = str(m.get("item-model"));
                Material bicon = mat(str(m.get("icon")), Material.NETHER_STAR);
                int bslot = (int) num(m.get("slot") == null ? -1 : m.get("slot"));
                ConfigurationSection raw = toSection(s, "bonuses._tmp_" + bonuses.size(), m);
                bonuses.add(new StatBonus(type, id, display, bicon, im, bslot, raw));
            }
            String statItemModel = str(s.getString("item-model"));
            StatDef def = new StatDef(key, name, icon, statItemModel, maxPoints, desc, attrs, bonuses);
            stats.put(key.toLowerCase(Locale.ROOT), def);
            statOrder.add(key.toLowerCase(Locale.ROOT));
        }
    }

    StatDef get(String key) { return key == null ? null : stats.get(key.toLowerCase(Locale.ROOT)); }
    List<StatDef> defs() { List<StatDef> out = new ArrayList<>(); for (String k : statOrder) { StatDef d = stats.get(k); if (d!=null) out.add(d);} return out; }

    int slot(String path, int def) { int v = plugin.getConfig().getInt(path, def); return v < 0 || v > 53 ? def : v; }
    List<Integer> slotList(String path, List<Integer> def) {
        List<Integer> raw = plugin.getConfig().getIntegerList(path);
        if (raw.isEmpty()) raw = def;
        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        for (int v : raw) if (v >= 0 && v <= 53) out.add(v);
        return new ArrayList<>(out);
    }
    String guiTitle(String path, String def) { String v = plugin.getConfig().getString(path, def); return v == null ? def : v; }
    Material guiFiller(String path, Material def) { Material m = Material.matchMaterial(plugin.getConfig().getString(path, def.name())); return m == null ? def : m; }

    private static FallbackSource parseFallback(String s) {
        if (s == null) return FallbackSource.LAST_KNOWN;
        try { return FallbackSource.valueOf(s.trim().toUpperCase(Locale.ROOT)); } catch (Exception e) { return FallbackSource.LAST_KNOWN; }
    }
    static String str(Object o) { return o == null ? "" : String.valueOf(o).trim(); }
    static Material mat(String name, Material def) { if (name == null || name.isBlank()) return def; Material m = Material.matchMaterial(name.trim()); return m == null ? def : m; }
    static double num(Object o) { if (o instanceof Number n) return n.doubleValue(); try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0; } }

    int maxPointsForLevel(int level) {
        int cap = Integer.MAX_VALUE;
        if (!statPointCaps.isEmpty()) {
            cap = 0;
            for (int[] e : statPointCaps) {
                if (level >= e[0]) cap = e[1];
                else break;
            }
        }
        // never above configured stat max-points
        return cap;
    }

    int effectiveMaxPoints(StatDef def, int level) {
        int byLevel = maxPointsForLevel(level);
        return Math.max(0, Math.min(def.maxPoints(), byLevel));
    }

    void setStatSlot(String statKey, int slot) {
        plugin.getConfig().set("gui.repartir.stat-slots." + statKey.toLowerCase(Locale.ROOT), slot);
        plugin.saveConfig(); reload();
    }
    void setAttributeSlot(String statKey, String attrId, int slot) { setListIntField(statKey, "attributes", "id", attrId, "slot", slot); }
    void setBonusSlot(String statKey, String bonusId, int slot) { setListIntField(statKey, "bonuses", "id", bonusId, "slot", slot); }
    void setAttributeItemModel(String statKey, String attrId, String model) { setListStringField(statKey, "attributes", "id", attrId, "item-model", model); }
    void setBonusItemModel(String statKey, String bonusId, String model) { setListStringField(statKey, "bonuses", "id", bonusId, "item-model", model); }
    void setStatItemModel(String statKey, String model) {
        plugin.getConfig().set("stats." + statKey.toLowerCase(Locale.ROOT) + ".item-model", model);
        plugin.saveConfig(); reload();
    }

    @SuppressWarnings("unchecked")
    private void setListIntField(String statKey, String listName, String matchKey, String matchValue, String field, int value) {
        String path = "stats." + statKey.toLowerCase(Locale.ROOT) + "." + listName;
        List<Map<?,?>> list = plugin.getConfig().getMapList(path);
        List<Map<String,Object>> out = new ArrayList<>();
        boolean done = false;
        for (Map<?,?> m : list) {
            Map<String,Object> nm = new LinkedHashMap<>();
            for (Map.Entry<?,?> e : m.entrySet()) nm.put(String.valueOf(e.getKey()), e.getValue());
            String mv = str(nm.get(matchKey));
            if (!done && mv.equalsIgnoreCase(matchValue)) { nm.put(field, value); done = true; }
            out.add(nm);
        }
        if (!done) {
            Map<String,Object> nm = new LinkedHashMap<>(); nm.put(matchKey, matchValue); nm.put(field, value); out.add(nm);
        }
        plugin.getConfig().set(path, out);
        plugin.saveConfig(); reload();
    }
    @SuppressWarnings("unchecked")
    private void setListStringField(String statKey, String listName, String matchKey, String matchValue, String field, String value) {
        String path = "stats." + statKey.toLowerCase(Locale.ROOT) + "." + listName;
        List<Map<?,?>> list = plugin.getConfig().getMapList(path);
        List<Map<String,Object>> out = new ArrayList<>();
        boolean done = false;
        for (Map<?,?> m : list) {
            Map<String,Object> nm = new LinkedHashMap<>();
            for (Map.Entry<?,?> e : m.entrySet()) nm.put(String.valueOf(e.getKey()), e.getValue());
            String mv = str(nm.get(matchKey));
            if (!done && mv.equalsIgnoreCase(matchValue)) { nm.put(field, value); done = true; }
            out.add(nm);
        }
        if (!done) {
            Map<String,Object> nm = new LinkedHashMap<>(); nm.put(matchKey, matchValue); nm.put(field, value); out.add(nm);
        }
        plugin.getConfig().set(path, out);
        plugin.saveConfig(); reload();
    }

    private static ConfigurationSection toSection(ConfigurationSection parent, String path, Map<?,?> map) {
        ConfigurationSection s = parent.createSection(path);
        for (Map.Entry<?,?> e : map.entrySet()) s.set(String.valueOf(e.getKey()), e.getValue());
        return s;
    }
}
