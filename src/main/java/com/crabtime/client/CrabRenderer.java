package com.crabtime.client;

import com.crabtime.CrabTime;
import com.crabtime.crab.Crab;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The crab. It turns sideways when it walks (crabs scuttle), and shows what it's carrying above its claws. */
public class CrabRenderer extends MobRenderer<Crab, CrabModel> {
    public CrabRenderer(EntityRendererProvider.Context ctx, CrabModel.Kind kind) {
        super(ctx, new CrabModel(kind), 0.35f);
        addLayer(new Held(this));
    }

    @Override public ResourceLocation getTextureLocation(Crab e) { return model.kind.texture(); }

    @Override
    protected void setupRotations(Crab e, PoseStack ps, float bob, float yaw, float pt, float scale) {
        super.setupRotations(e, ps, bob, yaw, pt, scale);
        ps.mulPose(Axis.YP.rotationDegrees(e.sideways(pt)));
    }

    static class Held extends RenderLayer<Crab, CrabModel> {
        Held(RenderLayerParent<Crab, CrabModel> parent) { super(parent); }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, Crab e, float limbSwing, float limbAmount, float pt, float age, float headYaw, float headPitch) {
            ItemStack s = e.held();
            float k = e.carryAmount(pt);
            if (s.isEmpty() || k < 0.3f) return;
            ps.pushPose();
            org.joml.Vector3f at = getParentModel().kind.heldAt();
            ps.translate(-at.x / 16f, (24f - at.y) / 16f, at.z / 16f);
            ps.mulPose(Axis.XP.rotationDegrees(180));
            ps.scale(0.3f * k, 0.3f * k, 0.3f * k);
            Minecraft.getInstance().getItemRenderer().renderStatic(s, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps, buf, e.level(), e.getId());
            ps.popPose();
        }
    }
}
