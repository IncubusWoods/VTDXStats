package dev.incubuswoods.vtdxstats;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

final class BridgeHook {
    private final VTDXStats plugin;
    private Object service;
    private Method isConfigured;
    private Method getProfile;

    BridgeHook(VTDXStats plugin) { this.plugin = plugin; }

    boolean hook() {
        try {
            Class<?> clazz = Class.forName("dev.incubuswoods.vtuberdexbridge.VTuberDexService");
            RegisteredServiceProvider<?> reg = Bukkit.getServicesManager().getRegistration(clazz);
            if (reg == null) return false;
            service = reg.getProvider();
            isConfigured = clazz.getMethod("isConfigured");
            getProfile = clazz.getMethod("getProfile", UUID.class);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("VTuberDexBridge hook failed: " + t.getMessage());
            return false;
        }
    }

    boolean isAvailable() {
        try { return service != null && isConfigured != null && Boolean.TRUE.equals(isConfigured.invoke(service)); }
        catch (Throwable t) { return false; }
    }

    Optional<BridgeProfile> getProfile(UUID uuid) {
        if (service == null || getProfile == null || uuid == null) return Optional.empty();
        try {
            Object p = getProfile.invoke(service, uuid);
            if (p == null) return Optional.empty();
            Class<?> c = p.getClass();
            boolean linked = (boolean) c.getMethod("linked").invoke(p);
            long gameLevel = ((Number) c.getMethod("gameLevel").invoke(p)).longValue();
            long webLevel = ((Number) c.getMethod("webLevel").invoke(p)).longValue();
            String name = (String) c.getMethod("name").invoke(p);
            return Optional.of(new BridgeProfile(linked, gameLevel, webLevel, name));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    record BridgeProfile(boolean linked, long gameLevel, long webLevel, String name) {}
}
