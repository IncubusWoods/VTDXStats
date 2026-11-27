package dev.incubuswoods.vtdxstats;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

final class PlayerStore {
    private final VTDXStats plugin;
    private final File file;
    private YamlConfiguration yml;
    private final Map<UUID, PlayerStatData> cache = new HashMap<>();

    PlayerStore(VTDXStats plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        reload();
    }

    synchronized void reload() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        yml = YamlConfiguration.loadConfiguration(file);
        cache.clear();
        ConfigurationSection players = yml.getConfigurationSection("players");
        if (players == null) return;
        for (String key : players.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(key); } catch (Exception e) { continue; }
            ConfigurationSection s = players.getConfigurationSection(key);
            if (s == null) continue;
            Map<String,Integer> points = new HashMap<>();
            ConfigurationSection ps = s.getConfigurationSection("points");
            if (ps != null) for (String k : ps.getKeys(false)) points.put(k.toLowerCase(Locale.ROOT), Math.max(0, ps.getInt(k, 0)));
            Set<String> disabled = new HashSet<>();
            for (String d : s.getStringList("disabled-scales")) if (d != null && !d.isBlank()) disabled.add(d.toLowerCase(Locale.ROOT));
            int lastKnown = Math.max(0, s.getInt("last-known-level", 0));
            cache.put(uuid, new PlayerStatData(points, disabled, lastKnown));
        }
    }

    synchronized PlayerStatData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, k -> PlayerStatData.empty());
    }

    synchronized void setLastKnownLevel(UUID uuid, int level) {
        PlayerStatData d = get(uuid);
        cache.put(uuid, new PlayerStatData(d.points(), d.disabledScales(), Math.max(0, level)));
    }

    synchronized void setPoints(UUID uuid, String stat, int points) {
        PlayerStatData d = get(uuid);
        d.points().put(stat.toLowerCase(Locale.ROOT), Math.max(0, points));
    }

    synchronized void resetPoints(UUID uuid) {
        PlayerStatData d = get(uuid);
        d.points().clear();
    }

    synchronized boolean toggleDisabled(UUID uuid, String stat) {
        PlayerStatData d = get(uuid);
        String k = stat.toLowerCase(Locale.ROOT);
        if (d.disabledScales().contains(k)) { d.disabledScales().remove(k); return false; }
        d.disabledScales().add(k); return true;
    }

    synchronized boolean isDisabled(UUID uuid, String stat) {
        return get(uuid).disabledScales().contains(stat.toLowerCase(Locale.ROOT));
    }

    synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerStatData> e : cache.entrySet()) {
            String base = "players." + e.getKey();
            PlayerStatData d = e.getValue();
            for (Map.Entry<String,Integer> p : d.points().entrySet()) out.set(base + ".points." + p.getKey(), p.getValue());
            if (!d.disabledScales().isEmpty()) out.set(base + ".disabled-scales", new ArrayList<>(d.disabledScales()));
            if (d.lastKnownLevel() > 0) out.set(base + ".last-known-level", d.lastKnownLevel());
        }
        try { out.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Could not save data.yml: " + ex.getMessage()); }
    }

    synchronized void savePlayer(UUID uuid) { save(); } // simple; small data
}
