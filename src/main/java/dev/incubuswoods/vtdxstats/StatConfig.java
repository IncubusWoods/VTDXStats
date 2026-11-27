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
                attrs.add(new StatAttribute(id, pp, cap, display));
            }
            List<StatBonus> bonuses = new ArrayList<>();
            for (Map<?,?> m : s.getMapList("bonuses")) {
                String type = str(m.get("type")).toUpperCase(Locale.ROOT);
                String id = str(m.get("id")); if (id.isBlank()) id = type;
                String display = str(m.get("display")); if (display.isBlank()) display = id;
                ConfigurationSection raw = toSection(s, "bonuses._tmp_" + bonuses.size(), m);
                bonuses.add(new StatBonus(type, id, display, raw));
            }
            StatDef def = new StatDef(key, name, icon, maxPoints, desc, attrs, bonuses);
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
    static double num(Object o) { if (o instanceof Number n) return n.doubleValue(); try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0; } }

    private static ConfigurationSection toSection(ConfigurationSection parent, String path, Map<?,?> map) {
        ConfigurationSection s = parent.createSection(path);
        for (Map.Entry<?,?> e : map.entrySet()) s.set(String.valueOf(e.getKey()), e.getValue());
        return s;
    }
}
