package com.crabtime.client;

import com.crabtime.CrabTime;
import com.crabtime.ModRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;

@EventBusSubscriber(modid = CrabTime.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModRegistry.CRAB.get(), CrabRenderer::new);
    }

    @SubscribeEvent
    public static void items(RegisterClientExtensionsEvent e) {
        e.registerItem(new IClientItemExtensions() {
            private ClawRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new ClawRenderer();
                return renderer;
            }
        }, ModRegistry.BIG_CRAB_CLAW.get());
    }

    private ClientSetup() {}
}
