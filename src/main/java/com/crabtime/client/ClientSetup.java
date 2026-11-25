package com.crabtime.client;

import com.crabtime.CrabTime;
import com.crabtime.ModRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

@EventBusSubscriber(modid = CrabTime.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModRegistry.CRAB.get(), ctx -> new CrabRenderer(ctx, CrabModel.BEACH));
        e.registerEntityRenderer(ModRegistry.CROP_CRAB.get(), ctx -> new CrabRenderer(ctx, CrabModel.CROP));
    }

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent e) { e.register(ModRegistry.NEST_MENU.get(), CrabNestScreen::new); }

    @SubscribeEvent
    public static void items(RegisterClientExtensionsEvent e) {
        e.registerItem(new IClientItemExtensions() {
            private ClawRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new ClawRenderer();
                return renderer;
            }

            /** Hold the claw steady in first person: no vanilla arm swing, so its own attack snap shows. */
            @Override
            public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                                   float partialTick, float equipProcess, float swingProcess) {
                int side = arm == HumanoidArm.RIGHT ? 1 : -1;
                pose.translate(side * 0.56f, -0.52f + equipProcess * -0.6f, -0.72f);
                return true;
            }
        }, ModRegistry.BIG_CRAB_CLAW.get());
    }

    private ClientSetup() {}
}
