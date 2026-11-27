package dev.incubuswoods.vtdxstats;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;

final class StatService {
    private final VTDXStats plugin;

    StatService(VTDXStats plugin) { this.plugin = plugin; }

    int resolveLevel(UUID uuid) {
        BridgeHook.BridgeProfile p = plugin.bridge().getProfile(uuid).orElse(null);
        StatConfig cfg = plugin.cfg();
        if (p != null && p.linked() && plugin.bridge().isAvailable()) {
            int lvl = (int) Math.max(1, Math.min(cfg.maxLevel, p.gameLevel()));
            plugin.store().setLastKnownLevel(uuid, lvl);
            plugin.store().savePlayer(uuid);
            return lvl;
        }
        return switch (cfg.fallbackSource) {
            case NONE -> 1;
            case FIXED -> Math.max(1, Math.min(cfg.maxLevel, cfg.fallbackFixedLevel));
            case LAST_KNOWN -> {
                int last = plugin.store().get(uuid).lastKnownLevel();
                if (last > 0) yield Math.max(1, Math.min(cfg.maxLevel, last));
                yield Math.max(1, Math.min(cfg.maxLevel, cfg.fallbackFixedLevel));
            }
        };
    }

    boolean hasUsableProfile(UUID uuid) {
        BridgeHook.BridgeProfile p = plugin.bridge().getProfile(uuid).orElse(null);
        if (p != null && p.linked() && plugin.bridge().isAvailable()) return true;
        StatConfig cfg = plugin.cfg();
        if (cfg.fallbackSource == FallbackSource.LAST_KNOWN && plugin.store().get(uuid).lastKnownLevel() > 0) return true;
        return cfg.fallbackSource == FallbackSource.FIXED;
    }

    int totalPoints(UUID uuid) { return resolveLevel(uuid) * plugin.cfg().pointsPerLevel; }

    int spentPoints(UUID uuid) {
        PlayerStatData d = plugin.store().get(uuid);
        int sum = 0;
        for (StatDef def : plugin.cfg().defs()) sum += Math.max(0, d.points().getOrDefault(def.key(), 0));
        return sum;
    }

    int availablePoints(UUID uuid) { return Math.max(0, totalPoints(uuid) - spentPoints(uuid)); }

    int getPoints(UUID uuid, String stat) { return Math.max(0, plugin.store().get(uuid).points().getOrDefault(stat.toLowerCase(Locale.ROOT), 0)); }

    boolean isStatDisabled(UUID uuid, String stat) { return plugin.store().isDisabled(uuid, stat); }

    boolean toggleStatDisabled(UUID uuid, String stat) {
        boolean now = plugin.store().toggleDisabled(uuid, stat);
        plugin.store().savePlayer(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) apply(p);
        return now;
    }

    int addPoints(UUID uuid, String stat, int amount) {
        StatDef def = plugin.cfg().get(stat);
        if (def == null || amount <= 0) return 0;
        if (isStatDisabled(uuid, stat)) return 0;
        int cur = getPoints(uuid, stat);
        int can = Math.min(amount, Math.max(0, def.maxPoints() - cur));
        can = Math.min(can, availablePoints(uuid));
        if (can <= 0) return 0;
        plugin.store().setPoints(uuid, def.key(), cur + can);
        plugin.store().savePlayer(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) apply(p);
        return can;
    }

    void reset(UUID uuid) {
        plugin.store().resetPoints(uuid);
        plugin.store().savePlayer(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) apply(p);
    }

    void clampRefund(UUID uuid) {
        boolean changed = false;
        PlayerStatData d = plugin.store().get(uuid);
        for (StatDef def : plugin.cfg().defs()) {
            int cur = Math.max(0, d.points().getOrDefault(def.key(), 0));
            if (cur > def.maxPoints()) {
                d.points().put(def.key(), def.maxPoints());
                changed = true;
            }
        }
        // also clamp total over level*pointsPerLevel
        int total = totalPoints(uuid);
        int spent = 0; for (StatDef def : plugin.cfg().defs()) spent += Math.max(0, d.points().getOrDefault(def.key(),0));
        if (spent > total) {
            // reduce from last stats in order for determinism
            List<StatDef> defs = plugin.cfg().defs();
            for (int i = defs.size()-1; i>=0 && spent > total; i--) {
                StatDef def = defs.get(i);
                int cur = Math.max(0, d.points().getOrDefault(def.key(),0));
                int take = Math.min(cur, spent-total);
                if (take>0) { d.points().put(def.key(), cur-take); spent -= take; changed = true; }
            }
        }
        if (changed) plugin.store().savePlayer(uuid);
    }

    void apply(Player player) {
        if (player == null) return;
        clampRefund(player.getUniqueId());
        UUID uuid = player.getUniqueId();
        for (StatDef def : plugin.cfg().defs()) {
            boolean disabled = isStatDisabled(uuid, def.key());
            int pts = disabled ? 0 : getPoints(uuid, def.key());
            for (StatAttribute attr : def.attributes()) {
                double value = disabled ? 0.0 : attr.valueFor(pts, def.maxPoints());
                plugin.mythic().apply(player, attr.id(), value);
            }
            for (StatBonus bonus : def.bonuses()) {
                if ("MYTHICLIB_STAT".equalsIgnoreCase(bonus.type())) {
                    String bstat = bonus.raw().getString("stat", "");
                    double bval = bonus.raw().getDouble("value", 0.0);
                    boolean active = !disabled && pts >= def.maxPoints();
                    plugin.mythic().apply(player, bstat, active ? bval : 0.0);
                }
            }
        }
    }

    void clear(Player player) { plugin.mythic().clearPlayer(player); }

    String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1.0e-9) return String.valueOf((long)Math.rint(v));
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
