package com.crabtime.client;

import com.crabtime.CrabTime;
import com.crabtime.ModRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the Big Crab Claw from blockbench/big_crab_claw.bbmodel and plays its animations:
 * claw idle (just holding it), claw grabing (the moment it grabs a mob), claw closed (while carrying), claw attack (swinging).
 * Its size and angle in each view come from models/item/big_crab_claw.json.
 */
public class ClawRenderer extends BlockEntityWithoutLevelRenderer {
    private final ResourceLocation RIG, TEX;
    private final boolean farming;
    private float[][] pose;
    private Matrix4f[] m;

    /** big_crab_claw or farming_claw. */
    public ClawRenderer(String name) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        RIG = CrabTime.id("rigs/" + name + ".json");
        TEX = CrabTime.id("textures/item/" + name + ".png");
        farming = name.equals("farming_claw");
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        CrabRig rig = CrabRig.get(RIG);
        if (pose == null) {
            pose = rig.newPose();
            m = new Matrix4f[rig.bones];
            for (int i = 0; i < m.length; i++) m[i] = new Matrix4f();
        }
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(true);
        float now = (mc.level != null ? mc.level.getGameTime() : 0) + pt;
        Long grabbed = farming ? null : stack.get(ModRegistry.CLAW_GRABBED.get());
        CrabRig.Anim a;
        float t;
        boolean right = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        boolean mine = mc.player != null && ItemStack.isSameItem(mc.player.getMainHandItem(), stack) && right;
        if (farming) {
            a = rig.anim("idle"); t = 0;
            if (mine && mc.player.isUsingItem() && ItemStack.isSameItem(mc.player.getUseItem(), stack)) {
                float held = (mc.player.getTicksUsingItem() + pt) / 20f;
                CrabRig.Anim grow = rig.anim("growing crop");
                if (grow != null && held < grow.length) { a = grow; t = held; }
                else { a = rig.anim("growing crop loop"); t = held; }
            } else if (mine) {
                float swing = mc.player.getAttackAnim(pt);
                CrabRig.Anim hoe = rig.anim("hoeing / harvasting");
                if (swing > 0 && hoe != null) { a = hoe; t = swing * hoe.length; }
            }
        } else if (grabbed != null) {
            float since = Math.max(0, (now - grabbed) / 20f);
            CrabRig.Anim grab = rig.anim("claw grabing");
            if (grab != null && since < grab.length) { a = grab; t = since; }
            else { a = rig.anim("claw closed"); t = since; }
        } else {
            a = rig.anim("claw idle");
            t = now / 20f;
            CrabRig.Anim hit = rig.anim("claw attack");
            if (right && hit != null && mc.player != null && ItemStack.isSameItem(mc.player.getMainHandItem(), stack)) {
                float swing = mc.player.getAttackAnim(pt);
                if (swing > 0) { a = hit; t = swing * hit.length; }
            }
        }
        if (a != null) a.sample(t, pose); else rig.resetPose(pose);
        rig.matrices(pose, m);

        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
        PoseStack.Pose last = ps.last();
        Vector3f p = new Vector3f(), n = new Vector3f();
        Matrix4f id = new Matrix4f();
        int orb = farming ? rig.boneIndex("grow orb") : -2;
        for (float[] f : rig.faces) {
            int b = (int) f[19];
            int lit = b == orb ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : light;
            Matrix4f mg = b >= 0 ? m[b] : id;
            mg.transformDirection(n.set(f[16], f[17], f[18])).normalize();
            for (int c = 0; c < 4; c++) {
                mg.transformPosition(p.set(f[c * 3], f[c * 3 + 1], f[c * 3 + 2]));
                float u = (c == 0 || c == 3) ? f[12] : f[14], v = (c < 2) ? f[13] : f[15];
                // Blockbench entity space (centred on x/z = 0) to item space (0..16)
                vc.addVertex(last, (p.x + 8f) / 16f, p.y / 16f, (p.z + 8f) / 16f).setColor(0xFFFFFFFF).setUv(u, v)
                        .setOverlay(overlay).setLight(lit).setNormal(last, n.x, n.y, n.z);
            }
        }
    }
}
