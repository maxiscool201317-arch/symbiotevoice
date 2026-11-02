package com.symbiotevoice;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reads the Symbiote mod's data by reflection, so this mod compiles without it
 * and keeps working (with defaults) if its internals change.
 */
final class SymbioteState {
    record Info(boolean available, boolean bonded, String strain, String stage, String mood,
                String temperament, double bond) {}

    private static final String PKG = "com.scout.symbiote.tracker.";

    static Info read(ServerPlayer sp) {
        String fallback = VoiceConfig.DEFAULT_STRAIN.get().toUpperCase();
        try {
            Class<?> trackerCls = Class.forName(PKG + "SymbioteTracker");
            Method get = trackerCls.getMethod("get", net.minecraft.server.level.ServerLevel.class);
            Object tracker = get.invoke(null, sp.serverLevel());
            Method peek = trackerCls.getMethod("peek", UUID.class);
            Object profile = peek.invoke(tracker, sp.getUUID());
            if (profile == null) {
                return new Info(true, false, fallback, "UNBONDED", "CONTENT", "NONE", 0);
            }
            String strain = enumName("SymbioteStrain", field(profile, "strain"));
            String stage = enumName("BondStage", field(profile, "stage"));
            String mood = enumName("MoodEngine$Mood", field(profile, "moodOrdinal"));
            String temper = enumName("MoodEngine$Temperament", field(profile, "temperamentOrdinal"));
            Object bondVal = field(profile, "bond");
            double bond = bondVal instanceof Number n ? n.doubleValue() : 0;
            if (strain == null) strain = fallback;
            if (stage == null) stage = "ATTACHED";
            boolean bonded = !"UNBONDED".equals(stage);
            return new Info(true, bonded, strain, stage,
                    mood == null ? "CONTENT" : mood, temper == null ? "NONE" : temper, bond);
        } catch (Throwable t) {
            // Symbiote not present or internals differ: behave as a bonded guardian.
            return new Info(false, true, fallback, "COOPERATIVE", "CONTENT", "NONE", 0);
        }
    }

    private static Object field(Object o, String name) {
        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String enumName(String enumClass, Object val) {
        if (val == null) return null;
        if (val instanceof Enum<?> e) return e.name();
        if (val instanceof Number n) {
            try {
                Object[] consts = Class.forName(PKG + enumClass).getEnumConstants();
                int i = n.intValue();
                if (consts != null && i >= 0 && i < consts.length) return ((Enum<?>) consts[i]).name();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private SymbioteState() {}
}
