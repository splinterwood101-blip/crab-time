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
    private CrabRig rig;
    private Matrix4f[] m;
    private float[][] poseA, poseB, pose;
    private float rootDrop; // model pixels, for the bury fallback

    /** Where the crab holds its find (model pixels, Blockbench space). */
    public static final Vector3f HELD_AT = new Vector3f(0, 8f, -2.5f);

    private void init() {
        if (rig != null) return;
        rig = CrabRig.get();
        m = new Matrix4f[rig.bones];
        for (int i = 0; i < m.length; i++) m[i] = new Matrix4f();
        poseA = rig.newPose(); poseB = rig.newPose(); pose = rig.newPose();
    }

    /** One-shot moves sent by the server, mapped to the animation names in the Blockbench file. */
    private static String fileName(String move) {
        return switch (move) {
            case "warn" -> "warning";
            case "pinch" -> "attack";
            case "snip" -> "leash cut";
            case "shed" -> "shead";
            default -> move; // dig, bury
        };
    }

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
            if ("bury".equals(move)) { // no bury in the file yet: sink into the sand
                float bt = e.animTime(pt);
                rootDrop = 7f * ease(bt / 0.7f) * (1 - ease((bt - 1.7f) / 0.6f));
            }
            if (e.carryAmount(pt) > 0.5f) want = "walking with item";
            else if (limbAmount > 0.08f) want = "walking";
            else want = "claw idle";
            if (rig.anim(want) == null) want = "claw idle";
            t = 0;
        }
        AnimState s = e.animState;
        if (!want.equals(s.cur)) {
            s.prev = s.cur; s.prevStart = s.curStart; s.switchAt = age;
            s.cur = want; s.curStart = age;
        }
        if (!oneShot) {
            t = (age - s.curStart) / 20f;
            // carrying while standing still: hold the claws up but don't walk
            if ("walking with item".equals(want) && limbAmount < 0.08f) t = 0;
        }
        CrabRig.Anim a = rig.anim(want);
        a.sample(t, poseB);
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
