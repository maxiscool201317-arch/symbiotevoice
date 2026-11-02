package com.symbiotevoice;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Hides the Symbiote mod's written whisper lines (translation keys "symbiote.voice.*"). Client only. */
@Mod.EventBusSubscriber(modid = "symbiotevoice", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientChatFilter {

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        if (!VoiceConfig.HIDE_WRITTEN_LINES.get()) return;
        if (isSymbioteLine(event.getMessage())) event.setCanceled(true);
    }

    private static boolean isSymbioteLine(Component c) {
        if (c == null) return false;
        if (c.getContents() instanceof TranslatableContents t) {
            if (t.getKey().startsWith("symbiote.voice.")) return true;
            for (Object arg : t.getArgs()) {
                if (arg instanceof Component ac && isSymbioteLine(ac)) return true;
            }
        }
        for (Component sibling : c.getSiblings()) {
            if (isSymbioteLine(sibling)) return true;
        }
        return false;
    }

    private ClientChatFilter() {}
}
