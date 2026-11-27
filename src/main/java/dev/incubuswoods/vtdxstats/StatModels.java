package dev.incubuswoods.vtdxstats;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

record StatAttribute(String id, double perPoint, double cap, String display) {
    double valueFor(int points, int maxPoints) {
        if (maxPoints <= 0) return 0;
        // preserve VTuberDex linear scaling: cap is value at maxPoints; proportional by points/maxPoints
        return cap * Math.min(1.0, Math.max(0, points) / (double) maxPoints);
    }
}

record StatBonus(String type, String id, String display, ConfigurationSection raw) {}

record StatDef(String key, String name, Material icon, int maxPoints, String description,
               List<StatAttribute> attributes, List<StatBonus> bonuses) {}

record PlayerStatData(Map<String,Integer> points, Set<String> disabledScales, int lastKnownLevel) {
    static PlayerStatData empty() { return new PlayerStatData(new HashMap<>(), new HashSet<>(), 0); }
}

enum FallbackSource { NONE, FIXED, LAST_KNOWN }
