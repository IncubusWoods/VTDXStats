package dev.incubuswoods.vtdxstats;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

final class ConfigUpdater {
    private ConfigUpdater() {}

    static boolean update(JavaPlugin plugin, String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) { plugin.saveResource(fileName, false); return true; }
        try (InputStream in = plugin.getResource(fileName)) {
            if (in == null) return false;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
            boolean changed = merge(defaults, current, "");
            if (changed) {
                backup(plugin, file);
                current.options().copyHeader(true);
                current.save(file);
                plugin.getLogger().info("Updated " + fileName + " with missing default keys (backup created).");
            }
            return changed;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not auto-update " + fileName + ": " + e.getMessage());
            return false;
        }
    }

    private static boolean merge(ConfigurationSection defaults, ConfigurationSection current, String path) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            String childPath = path.isEmpty() ? key : path + "." + key;
            Object defVal = defaults.get(key);
            if (!current.contains(key)) {
                current.set(key, defVal);
                changed = true;
                continue;
            }
            Object curVal = current.get(key);
            if (defVal instanceof ConfigurationSection ds && curVal instanceof ConfigurationSection cs) {
                if (merge(ds, cs, childPath)) changed = true;
            }
            // keep user value for scalars/lists; don't overwrite
        }
        return changed;
    }

    private static void backup(JavaPlugin plugin, File file) {
        try {
            String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            File bak = new File(file.getParentFile(), file.getName() + ".bak-" + ts);
            Files.copy(file.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not backup " + file.getName() + ": " + e.getMessage());
        }
    }
}
