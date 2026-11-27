package dev.incubuswoods.vtdxstats;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class MythicLibHook {
    private final VTDXStats plugin;
    private final Map<String, Double> applied = new ConcurrentHashMap<>(); // uuid|stat -> value

    MythicLibHook(VTDXStats plugin) { this.plugin = plugin; }

    boolean isEnabled() {
        return plugin.cfg().mythicEnabled && Bukkit.getPluginManager().getPlugin("MythicLib") != null;
    }

    void apply(Player player, String statId, double value) {
        if (player == null || statId == null) return;
        String mapKey = player.getUniqueId() + "|" + statId.toUpperCase(Locale.ROOT);
        Double old = applied.get(mapKey);
        if (old != null && Math.abs(old - value) < 1.0e-9) return;
        // remove then add if non-zero
        dispatch(plugin.cfg().mythicRemove, player, statId, value);
        if (Math.abs(value) > 1.0e-9) dispatch(plugin.cfg().mythicAdd, player, statId, value);
        applied.put(mapKey, value);
    }

    void remove(Player player, String statId) {
        if (player == null || statId == null) return;
        String mapKey = player.getUniqueId() + "|" + statId.toUpperCase(Locale.ROOT);
        dispatch(plugin.cfg().mythicRemove, player, statId, 0);
        applied.remove(mapKey);
    }

    void clearPlayer(Player player) {
        if (player == null) return;
        String prefix = player.getUniqueId().toString() + "|";
        for (String k : applied.keySet()) {
            if (k.startsWith(prefix)) {
                String stat = k.substring(prefix.length());
                dispatch(plugin.cfg().mythicRemove, player, stat, 0);
                applied.remove(k);
            }
        }
    }

    private void dispatch(String template, Player player, String statId, double value) {
        if (!isEnabled() || template == null || template.isBlank()) return;
        String cmd = template
                .replace("{player}", player.getName())
                .replace("{stat}", statId)
                .replace("{value}", format(value))
                .replace("{key}", plugin.cfg().mythicKey);
        CommandSender console = Bukkit.getConsoleSender();
        Bukkit.dispatchCommand(console, cmd);
    }

    private static String format(double v) {
        if (Math.abs(v - Math.rint(v)) < 1.0e-9) return String.valueOf((long) Math.rint(v));
        return String.format(Locale.ROOT, "%.4f", v);
    }
}
