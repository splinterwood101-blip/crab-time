package com.crabtime.client;

import com.crabtime.crab.Crab;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The crab, bones and moves exactly as on its design page. Angles in degrees; moves in page pixels (11 per model pixel). */
public class CrabModel extends EntityModel<Crab> {
    private static final float DEG = Mth.DEG_TO_RAD;
    static final int ROOT = 0, BODY = 1, EYES = 2, ARM_L = 3, ARM_R = 4, CLAW_L = 5, CLAW_R = 6, JAW_L = 7, JAW_R = 8, LEG0 = 9, N = 15;
    private final Matrix4f[] m = new Matrix4f[N];
    private final float[][] rot = new float[N][3], mv = new float[N][3];
    private boolean hideClawR;

    public CrabModel() { for (int i = 0; i < N; i++) m[i] = new Matrix4f(); }

    private static float ease(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    private static float seg(float t, float a, float b) { return ease((t - a) / (b - a)); }
    private static float mix(float a, float b, float k) { return a + (b - a) * k; }
    private static float pos(float x) { return Math.max(0, x); }

    /** legs: left 1..3 then right 1..3 */
    private static void legs(float[][] leg, float phase, float amp) {
        for (int s = 0; s < 2; s++)
            for (int i = 0; i < 3; i++) {
                float ph = phase + (i + 1) * 2.1f + (s == 1 ? Mth.PI : 0);
                leg[s * 3 + i][0] = Mth.cos(ph) * amp * 0.5f; leg[s * 3 + i][1] = 0; leg[s * 3 + i][2] = (s == 0 ? 1 : -1) * pos(Mth.sin(ph)) * amp;
            }
    }

    @Override
    public void setupAnim(Crab e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        float pt = age - e.tickCount, tt = age / 20f;
        String a = e.animName();
        float t = e.animTime(pt);
        float[] body = {0, 0, 0}, aL = {0, 0, 0}, aR = {0, 0, 0}, cL = {0, 0, 0}, cR = {0, 0, 0}, cRm = {0, 0, 0};
        float[] eye = {Mth.sin(tt * 0.7f) * 8, Mth.sin(tt * 0.5f) * 10, 0};
        float mx = 0, my = Mth.sin(tt * 2.2f) * 0.6f, mz = 0;
        float jL = (float) Math.pow(pos(Mth.sin(tt * 1.3f)), 8) * 22, jR = (float) Math.pow(pos(Mth.sin(tt * 1.3f + 2.2f)), 8) * 22;
        float[][] leg = new float[6][3];
        float walk = Math.min(1f, limbAmount * 2.5f);
        legs(leg, limbSwing * 1.6f, 24 * walk);
        body[2] = Mth.sin(limbSwing * 1.6f) * 2 * walk;
        float carry = e.carryAmount(pt);
        if (carry > 0.01f) {
            aL = new float[]{55 * carry, -25 * carry, 0}; aR = new float[]{55 * carry, 25 * carry, 0};
            cL = new float[]{10 * carry, 0, 0}; cR = new float[]{10 * carry, 0, 0}; jL = mix(jL, 8, carry); jR = mix(jR, 8, carry);
        }
        hideClawR = false;
        if ("dig".equals(a)) {
            float k = seg(t, 0, 0.3f) * (1 - seg(t, 2.2f, 2.6f));
            body = new float[]{-14 * k, 0, 0}; my += 8 * k;
            float sL = Mth.sin(t * 11), sR = Mth.sin(t * 11 + Mth.PI);
            aL = new float[]{-30 * k * pos(sL) - 10 * k, 0, 0}; aR = new float[]{-30 * k * pos(sR) - 10 * k, 0, 0};
            jL = jR = 25 * k;
            legs(leg, tt * 14, 6 * k);
        } else if ("warn".equals(a)) {
            float k = seg(t, 0, 0.3f) * (1 - seg(t, 1.4f, 1.8f));
            body = new float[]{12 * k, 0, 0}; my -= 10 * k;
            aL = new float[]{60 * k, -35 * k, -20 * k}; aR = new float[]{60 * k, 35 * k, 20 * k};
            cL = new float[]{-30 * k, 0, 0}; cR = new float[]{-30 * k, 0, 0};
            jL = jR = 35 * k * (0.6f + 0.4f * Math.abs(Mth.sin(t * 14)));
            eye = new float[]{-10 * k, 0, 0};
        } else if ("pinch".equals(a)) {
            float wind = seg(t, 0, 0.3f), hit = seg(t, 0.35f, 0.5f), back = seg(t, 0.6f, 1.0f), k = hit * (1 - back);
            mz = 40 * k - 10 * wind * (1 - hit);
            aR = new float[]{mix(25 * wind, -15, hit) * (1 - back), 0, 0};
            jR = mix(40 * wind, 0, hit) * (1 - back);
            body = new float[]{-6 * k, 0, 0};
        } else if ("snip".equals(a)) {
            float k = seg(t, 0, 0.25f) * (1 - seg(t, 1.1f, 1.4f));
            aL = new float[]{40 * k, -20 * k, 0}; cL = new float[]{-20 * k, 0, 0}; jL = k * Math.abs(Mth.sin(t * 10)) * 40;
        } else if ("shed".equals(a)) {
            float shake = t < 1 ? Mth.sin(t * 40) * 5 : 0;
            body = new float[]{0, shake, shake * 0.6f};
            float drop = seg(t, 1.0f, 1.5f);
            cRm = new float[]{30 * drop, 52 * drop, 20 * drop};
            hideClawR = t > 1.55f && t < 2.3f;
            if (t >= 1.55f) cRm = new float[]{0, 0, 0};
        } else if ("bury".equals(a)) {
            float dn = seg(t, 0.1f, 0.8f), up = seg(t, 1.7f, 2.3f);
            my += 48 * dn * (1 - up);
            legs(leg, tt * 16, 14 * dn * (1 - up));
        }
        for (float[] r : rot) java.util.Arrays.fill(r, 0);
        for (float[] r : mv) java.util.Arrays.fill(r, 0);
        mv[ROOT][0] = -mx / 11f; mv[ROOT][1] = -my / 11f; mv[ROOT][2] = -mz / 11f;
        rot[BODY] = body; rot[EYES] = eye;
        rot[ARM_L] = aL; rot[ARM_R] = aR; rot[CLAW_L] = cL; rot[CLAW_R] = cR;
        mv[CLAW_R][0] = -cRm[0] / 11f; mv[CLAW_R][1] = -cRm[1] / 11f; mv[CLAW_R][2] = -cRm[2] / 11f;
        rot[JAW_L] = new float[]{-jL, 0, 0}; rot[JAW_R] = new float[]{-jR, 0, 0};
        for (int i = 0; i < 6; i++) rot[LEG0 + i] = leg[i];
        for (int b = 0; b < N; b++) {
            int p = CrabModelData.PARENT[b];
            Matrix4f mb = m[b];
            if (p >= 0) mb.set(m[p]); else mb.identity();
            mb.translate(mv[b][0], mv[b][1], mv[b][2]);
            float[] pv = CrabModelData.PIVOT[b];
            mb.translate(pv[0], pv[1], pv[2]).rotateY(rot[b][1] * DEG).rotateX(rot[b][0] * DEG).rotateZ(rot[b][2] * DEG).translate(-pv[0], -pv[1], -pv[2]);
        }
    }

    /** Where the crab holds its find, in model space (same mapping as the vertices). */
    public static final Vector3f HELD_AT = new Vector3f(0, (24f - 12.5f) / 16f, -7.5f / 16f);

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vc, int light, int overlay, int color) {
        PoseStack.Pose pose = poseStack.last();
        float[] d = CrabModelData.DATA;
        int stride = CrabModelData.STRIDE;
        Vector3f p = new Vector3f(), n = new Vector3f();
        for (int f = 0; f < CrabModelData.GROUP.length; f++) {
            int o = f * stride, g = CrabModelData.GROUP[f];
            if (hideClawR && (g == CLAW_R || g == JAW_R)) continue;
            float u0 = d[o + 11], v0 = d[o + 12], u1 = d[o + 13], v1 = d[o + 14];
            Matrix4f mg = m[g];
            n.set(d[o + 15], d[o + 16], d[o + 17]);
            mg.transformDirection(n);
            for (int c = 0; c < 4; c++) {
                int i = (c == 1 || c == 2) ? 1 : 0, j = c >= 2 ? 1 : 0;
                float w = d[o + 9] * i, h = d[o + 10] * j;
                p.set(d[o] + d[o + 3] * w + d[o + 6] * h, d[o + 1] + d[o + 4] * w + d[o + 7] * h, d[o + 2] + d[o + 5] * w + d[o + 8] * h);
                mg.transformPosition(p);
                vc.addVertex(pose, -p.x / 16f, (24f - p.y) / 16f, p.z / 16f).setColor(color).setUv(u0 + (u1 - u0) * i, v0 + (v1 - v0) * j)
                        .setOverlay(overlay).setLight(light).setNormal(pose, -n.x, -n.y, n.z);
            }
        }
    }
}
