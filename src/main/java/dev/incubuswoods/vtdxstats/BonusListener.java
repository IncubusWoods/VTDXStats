package dev.incubuswoods.vtdxstats;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class BonusListener implements Listener {
    private final VTDXStats plugin;
    private final Set<UUID> aoeGuard = ConcurrentHashMap.newKeySet();

    BonusListener(VTDXStats plugin) { this.plugin = plugin; }

    private boolean bonusActive(Player player, StatDef def, StatBonus bonus) {
        if (player == null || def == null || bonus == null) return false;
        UUID uuid = player.getUniqueId();
        if (plugin.stats().isStatDisabled(uuid, def.key())) return false;
        int pts = plugin.stats().getPoints(uuid, def.key());
        if (pts < def.maxPoints()) return false;
        // bonus itself can be disabled by admin via scale toggle id
        return !plugin.store().isDisabled(uuid, bonus.id());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(e.getEntity() instanceof Player p)) return;
        for (StatDef def : plugin.cfg().defs()) {
            for (StatBonus b : def.bonuses()) {
                if (!"FALL_DAMAGE_IMMUNITY".equalsIgnoreCase(b.type())) continue;
                if (bonusActive(p, def, b)) { e.setCancelled(true); return; }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMagic(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        EntityDamageEvent.DamageCause cause = e.getCause();
        for (StatDef def : plugin.cfg().defs()) {
            for (StatBonus b : def.bonuses()) {
                if (!"MAGIC_DAMAGE_REDUCTION".equalsIgnoreCase(b.type())) continue;
                if (!bonusActive(p, def, b)) continue;
                List<String> causes = b.raw().getStringList("causes");
                if (!causes.isEmpty() && causes.stream().noneMatch(c -> c.equalsIgnoreCase(cause.name()))) continue;
                double mult = b.raw().getDouble("multiplier", 0.5);
                e.setDamage(e.getDamage() * mult);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAoe(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (victim instanceof Player) return;
        for (StatDef def : plugin.cfg().defs()) {
            for (StatBonus b : def.bonuses()) {
                if (!"AOE_DAMAGE".equalsIgnoreCase(b.type())) continue;
                if (!bonusActive(attacker, def, b)) continue;
                double damage = b.raw().getDouble("damage", 3.0);
                double radius = b.raw().getDouble("radius", 3.0);
                boolean excludePlayers = b.raw().getBoolean("exclude-players", true);
                UUID vid = victim.getUniqueId();
                if (!aoeGuard.add(vid)) return;
                try {
                    for (Entity near : victim.getWorld().getNearbyEntities(victim.getLocation(), radius, radius, radius,
                            ent -> ent instanceof LivingEntity le && ent != victim && (!excludePlayers || !(ent instanceof Player)))) {
                        LivingEntity le = (LivingEntity) near;
                        if (!aoeGuard.add(le.getUniqueId())) continue;
                        try { le.damage(damage, attacker); } finally { aoeGuard.remove(le.getUniqueId()); }
                    }
                } finally { aoeGuard.remove(vid); }
                return;
            }
        }
    }
}
