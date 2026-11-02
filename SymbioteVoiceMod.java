package com.symbiotevoice;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod("symbiotevoice")
public class SymbioteVoiceMod {
    public static final Logger LOGGER = LogUtils.getLogger();

    public SymbioteVoiceMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, VoiceConfig.SPEC, "symbiotevoice-common.toml");
        MinecraftForge.EVENT_BUS.register(new ChatterHandler());
    }
}
