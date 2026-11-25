package com.crabtime.client;

import com.crabtime.crab.AnimState;
import com.crabtime.crab.Crab;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The crab from blockbench/crab.bbmodel, playing its own animations:
 * claw idle, walking, walking with item (carrying), dig, warning, attack, leash cut, shead (shed) and bury.
 */
public class CrabModel extends EntityModel<Crab> {
    /**
     * What differs between crab kinds: the rig file, the animation names in its Blockbench file, and where it holds things.
     * A null loop name means "no animation, just the model's rest pose".
     */
    public record Kind(net.minecraft.resources.ResourceLocation rig, net.minecraft.resources.ResourceLocation texture, java.util.Map<String, String> moves,
                       String idle, String walk, String carry, String flee, Vector3f heldAt) {}

    public static final Kind BEACH = new Kind(CrabRig.FILE, com.crabtime.CrabTime.id("textures/entity/crab.png"),
            java.util.Map.of("warn", "warning", "pinch", "attack", "snip", "leash cut", "shed", "shead"),
            "claw idle", "walking", "walking with item", null, new Vector3f(0, 7f, -3.5f));
    public static final Kind CROP = new Kind(com.crabtime.CrabTime.id("models/entity/crop_crab.json"), com.crabtime.CrabTime.id("textures/entity/crop_crab.png"),
            java.util.Map.of("dig", "diging/croping", "snip", "cuting leash", "shed", "sheading and regrowing"),
            null, "walking", "walking with item", "fleeing", new Vector3f(0, 2.5f, -6f));

    public final Kind kind;
    private CrabRig rig;
    private Matrix4f[] m;
    private float[][] poseA, poseB, pose;
    private float rootDrop; // model pixels, for the bury fallback

    public CrabModel(Kind kind) { this.kind = kind; }

    private void init() {
        if (rig != null) return;
        rig = CrabRig.get(kind.rig());
        m = new Matrix4f[rig.bones];
        for (int i = 0; i < m.length; i++) m[i] = new Matrix4f();
        poseA = rig.newPose(); poseB = rig.newPose(); pose = rig.newPose();
    }

    /** One-shot moves sent by the server, mapped to the animation names in the Blockbench file. */
    private String fileName(String move) { return kind.moves().getOrDefault(move, move); }

    @Override
    public void setupAnim(Crab e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        init();
        float pt = age - e.tickCount;
        String move = e.animName();
        String want;
        float t;
        boolean oneShot = false;
        rootDrop = 0;
        if (move != null && rig.anim(fileName(move)) != null) {
            want = fileName(move); t = e.animTime(pt); oneShot = true;
        } else {
            if (e.isFleeing() && kind.flee() != null) want = kind.flee();
            else if (e.carryAmount(pt) > 0.5f) want = kind.carry();
            else if (limbAmount > 0.08f) want = kind.walk();
            else want = kind.idle();
            if (rig.anim(want) == null) want = kind.idle();
            t = 0;
        }
        AnimState s = e.animState;
        if (!java.util.Objects.equals(want, s.cur)) {
            s.prev = s.cur; s.prevStart = s.curStart; s.switchAt = age;
            s.cur = want; s.curStart = age;
        }
        if (!oneShot) {
            t = (age - s.curStart) / 20f;
            // carrying while standing still: hold the claws up but don't walk
            if (kind.carry().equals(want) && limbAmount < 0.08f) t = 0;
        }
        CrabRig.Anim a = rig.anim(want);
        if (a != null) a.sample(t, poseB); else rig.resetPose(poseB);
        float k = Mth.clamp((age - s.switchAt) / 4f, 0, 1);
        CrabRig.Anim p = rig.anim(s.prev);
        if (k < 1 && p != null) {
            p.sample((age - s.prevStart) / 20f, poseA);
            CrabRig.lerp(poseA, poseB, k, pose);
        } else CrabRig.lerp(poseB, poseB, 1, pose);
        rig.matrices(pose, m);
    }

    private static float ease(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vc, int light, int overlay, int color) {
        if (rig == null) return;
        PoseStack.Pose ps = poseStack.last();
        Vector3f p = new Vector3f(), n = new Vector3f();
        Matrix4f id = new Matrix4f();
        for (float[] f : rig.faces) {
            int b = (int) f[19];
            Matrix4f mg = b >= 0 ? m[b] : id;
            mg.transformDirection(n.set(f[16], f[17], f[18])).normalize();
            for (int c = 0; c < 4; c++) {
                mg.transformPosition(p.set(f[c * 3], f[c * 3 + 1], f[c * 3 + 2]));
                float u = (c == 0 || c == 3) ? f[12] : f[14], v = (c < 2) ? f[13] : f[15];
                vc.addVertex(ps, -p.x / 16f, (24f - p.y + rootDrop) / 16f, p.z / 16f).setColor(color).setUv(u, v)
                        .setOverlay(overlay).setLight(light).setNormal(ps, -n.x, -n.y, n.z);
            }
        }
    }
}
