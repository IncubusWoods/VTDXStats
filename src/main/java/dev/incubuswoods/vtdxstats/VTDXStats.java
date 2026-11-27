package dev.incubuswoods.vtdxstats;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class VTDXStats extends JavaPlugin implements Listener {
    private StatConfig cfg;
    private PlayerStore store;
    private BridgeHook bridge;
    private MythicLibHook mythic;
    private StatService stats;
    private Guis guis;
    final java.util.Map<java.util.UUID, PendingEdit> pendingEdits = new java.util.concurrent.ConcurrentHashMap<>();

    record PendingEdit(String type, String stat, String subType, String subId) {} // type SLOT or MODEL; subType attr/bonus/stat

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfigUpdater.update(this, "config.yml");
        reloadConfig();
        this.cfg = new StatConfig(this);
        this.store = new PlayerStore(this);
        this.bridge = new BridgeHook(this);
        this.bridge.hook();
        this.mythic = new MythicLibHook(this);
        this.stats = new StatService(this);
        this.guis = new Guis(this);

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BonusListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);

        PluginCommand cmd = getCommand("vtdxstats");
        if (cmd != null) {
            VTDXStatsCommand exec = new VTDXStatsCommand(this);
            cmd.setExecutor(exec);
            cmd.setTabCompleter(exec);
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try { new VTDXStatsExpansion(this).register(); } catch (Throwable ignored) {}
        }

        if (cfg.clampRefundOnReload) {
            for (Player p : Bukkit.getOnlinePlayers()) stats.clampRefund(p.getUniqueId());
        }
        getLogger().info("VTDXStats enabled. Bridge=" + bridge.isAvailable() + " MythicLib=" + mythic.isEnabled());
    }

    @Override
    public void onDisable() {
        if (store != null) store.save();
    }

    public void reloadAll() {
        reloadConfig();
        cfg.reload();
        store.reload();
        bridge.hook();
        if (cfg.clampRefundOnReload) {
            for (Player p : Bukkit.getOnlinePlayers()) { stats.clampRefund(p.getUniqueId()); stats.apply(p); }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            stats.clampRefund(p.getUniqueId());
            stats.apply(p);
        }, cfg.applyDelayTicks);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        store.savePlayer(e.getPlayer().getUniqueId());
    }

    String msg(String path) { String v = getConfig().getString(path); return v == null ? "" : v; }

    StatConfig cfg() { return cfg; }
    PlayerStore store() { return store; }
    BridgeHook bridge() { return bridge; }
    MythicLibHook mythic() { return mythic; }
    public StatService stats() { return stats; }
    Guis guis() { return guis; }
}
