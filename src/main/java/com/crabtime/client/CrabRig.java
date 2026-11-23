package com.crabtime.client;

import com.crabtime.CrabTime;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A Blockbench model (made by tools/bbmodel_to_json.py) and its animations, played the way Blockbench plays them:
 * bone = parent * translate(pivot + position) * rotZ * rotY * rotX * scale * translate(-pivot), angles in degrees.
 */
public final class CrabRig {
    public static final ResourceLocation FILE = CrabTime.id("models/entity/crab.json");
    private static final Map<ResourceLocation, CrabRig> CACHE = new HashMap<>();

    public static CrabRig get() { return get(FILE); }

    /** Any rig made by the converter (the crab, the Big Crab Claw, ...). */
    public static CrabRig get(ResourceLocation id) { return CACHE.computeIfAbsent(id, CrabRig::load); }

    public void resetPose(float[][] pose) {
        for (float[] b : pose) { b[0] = b[1] = b[2] = b[3] = b[4] = b[5] = 0; b[6] = b[7] = b[8] = 1; }
    }

    public final int bones;
    final String[] names;
    final int[] parent;
    final float[][] pivot, rest;
    /** per face: 4 corners (TL,TR,BR,BL) x,y,z | u1,v1,u2,v2 | normal | bone */
    final List<float[]> faces = new ArrayList<>();
    final Map<String, Anim> anims = new HashMap<>();

    private CrabRig(JsonObject o) {
        JsonArray bs = o.getAsJsonArray("bones");
        bones = bs.size();
        names = new String[bones]; parent = new int[bones]; pivot = new float[bones][]; rest = new float[bones][];
        for (int i = 0; i < bones; i++) {
            JsonObject b = bs.get(i).getAsJsonObject();
            names[i] = b.get("name").getAsString();
            parent[i] = b.get("parent").getAsInt();
            pivot[i] = vec(b.getAsJsonArray("pivot"));
            rest[i] = vec(b.getAsJsonArray("rot"));
        }
        for (JsonElement ce : o.getAsJsonArray("cubes")) {
            JsonObject c = ce.getAsJsonObject();
            float inf = c.has("inflate") ? c.get("inflate").getAsFloat() : 0;
            float[] a = vec(c.getAsJsonArray("from")), b = vec(c.getAsJsonArray("to")), org = vec(c.getAsJsonArray("origin")), r = vec(c.getAsJsonArray("rot"));
            for (int k = 0; k < 3; k++) { a[k] -= inf; b[k] += inf; }
            Matrix4f local = new Matrix4f().translate(org[0], org[1], org[2]).rotateZ(rad(r[2])).rotateY(rad(r[1])).rotateX(rad(r[0])).translate(-org[0], -org[1], -org[2]);
            int bone = c.get("bone").getAsInt();
            JsonObject fs = c.getAsJsonObject("faces");
            for (String f : fs.keySet()) {
                float[][] q = corners(f, a, b);
                float[] uv = vec(fs.getAsJsonArray(f));
                float[] n = normal(f);
                float[] out = new float[12 + 4 + 3 + 1];
                Vector3f p = new Vector3f();
                for (int i = 0; i < 4; i++) {
                    local.transformPosition(p.set(q[i][0], q[i][1], q[i][2]));
                    out[i * 3] = p.x; out[i * 3 + 1] = p.y; out[i * 3 + 2] = p.z;
                }
                System.arraycopy(uv, 0, out, 12, 4);
                Vector3f nn = local.transformDirection(new Vector3f(n[0], n[1], n[2])).normalize();
                out[16] = nn.x; out[17] = nn.y; out[18] = nn.z; out[19] = bone;
                faces.add(out);
            }
        }
        JsonObject as = o.getAsJsonObject("anims");
        for (String name : as.keySet()) anims.put(name.toLowerCase(Locale.ROOT), new Anim(as.getAsJsonObject(name), bones));
    }

    private static CrabRig load(ResourceLocation id) {
        try (Reader r = Minecraft.getInstance().getResourceManager().openAsReader(id)) {
            return new CrabRig(JsonParser.parseReader(r).getAsJsonObject());
        } catch (Exception e) {
            throw new IllegalStateException("Couldn't load " + id, e);
        }
    }

    static float rad(float d) { return d * (float) Math.PI / 180f; }

    private static float[] vec(JsonArray a) {
        float[] v = new float[a.size()];
        for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsFloat();
        return v;
    }

    /** TL, TR, BR, BL of a face as Blockbench lays its texture. */
    private static float[][] corners(String f, float[] a, float[] b) {
        float[] tl, tr, bl;
        switch (f) {
            case "north" -> { tl = new float[]{b[0], b[1], a[2]}; tr = new float[]{a[0], b[1], a[2]}; bl = new float[]{b[0], a[1], a[2]}; }
            case "south" -> { tl = new float[]{a[0], b[1], b[2]}; tr = new float[]{b[0], b[1], b[2]}; bl = new float[]{a[0], a[1], b[2]}; }
            case "west" -> { tl = new float[]{a[0], b[1], a[2]}; tr = new float[]{a[0], b[1], b[2]}; bl = new float[]{a[0], a[1], a[2]}; }
            case "east" -> { tl = new float[]{b[0], b[1], b[2]}; tr = new float[]{b[0], b[1], a[2]}; bl = new float[]{b[0], a[1], b[2]}; }
            case "up" -> { tl = new float[]{a[0], b[1], a[2]}; tr = new float[]{b[0], b[1], a[2]}; bl = new float[]{a[0], b[1], b[2]}; }
            default -> { tl = new float[]{a[0], a[1], b[2]}; tr = new float[]{b[0], a[1], b[2]}; bl = new float[]{a[0], a[1], a[2]}; }
        }
        float[] br = {tr[0] + bl[0] - tl[0], tr[1] + bl[1] - tl[1], tr[2] + bl[2] - tl[2]};
        return new float[][]{tl, tr, br, bl};
    }

    private static float[] normal(String f) {
        return switch (f) {
            case "north" -> new float[]{0, 0, -1};
            case "south" -> new float[]{0, 0, 1};
            case "east" -> new float[]{1, 0, 0};
            case "west" -> new float[]{-1, 0, 0};
            case "up" -> new float[]{0, 1, 0};
            default -> new float[]{0, -1, 0};
        };
    }

    public Anim anim(String name) { return name == null ? null : anims.get(name.toLowerCase(Locale.ROOT)); }

    /** Pose: per bone rot(3) pos(3) scale(3). */
    public float[][] newPose() {
        float[][] p = new float[bones][9];
        for (float[] b : p) { b[6] = b[7] = b[8] = 1; }
        return p;
    }

    public static void lerp(float[][] a, float[][] b, float k, float[][] out) {
        for (int i = 0; i < out.length; i++) for (int j = 0; j < 9; j++) out[i][j] = a[i][j] + (b[i][j] - a[i][j]) * k;
    }

    public void matrices(float[][] pose, Matrix4f[] out) {
        for (int i = 0; i < bones; i++) {
            Matrix4f m = out[i];
            if (parent[i] >= 0) m.set(out[parent[i]]); else m.identity();
            float[] pv = pivot[i], p = pose[i], r = rest[i];
            m.translate(pv[0] + p[3], pv[1] + p[4], pv[2] + p[5])
                    .rotateZ(rad(r[2] + p[2])).rotateY(rad(r[1] + p[1])).rotateX(rad(r[0] + p[0]))
                    .scale(p[6], p[7], p[8])
                    .translate(-pv[0], -pv[1], -pv[2]);
        }
    }

    public int boneIndex(String name) {
        for (int i = 0; i < bones; i++) if (names[i].equalsIgnoreCase(name)) return i;
        return -1;
    }

    // ---------- animations ----------
    public static final class Anim {
        public final float length;
        public final boolean loop;
        final Channel[][] ch; // [bone][rotation, position, scale]

        Anim(JsonObject o, int bones) {
            length = Math.max(0.05f, o.get("length").getAsFloat());
            loop = "loop".equals(o.get("loop").getAsString());
            ch = new Channel[bones][3];
            JsonObject bs = o.getAsJsonObject("bones");
            for (String k : bs.keySet()) {
                int b = Integer.parseInt(k);
                JsonObject c = bs.getAsJsonObject(k);
                if (c.has("rotation")) ch[b][0] = new Channel(c.getAsJsonArray("rotation"));
                if (c.has("position")) ch[b][1] = new Channel(c.getAsJsonArray("position"));
                if (c.has("scale")) ch[b][2] = new Channel(c.getAsJsonArray("scale"));
            }
        }

        public float wrap(float t) { return loop ? ((t % length) + length) % length : Math.min(t, length); }

        /** Writes this animation's pose at time t (seconds) into out. */
        public void sample(float t, float[][] out) {
            t = wrap(t);
            for (int b = 0; b < ch.length; b++) {
                float[] o = out[b];
                o[0] = o[1] = o[2] = o[3] = o[4] = o[5] = 0; o[6] = o[7] = o[8] = 1;
                for (int c = 0; c < 3; c++) if (ch[b][c] != null) ch[b][c].sample(t, o, c * 3);
            }
        }
    }

    static final class Channel {
        final float[] time; final float[][] val; final int[] interp;

        Channel(JsonArray a) {
            int n = a.size();
            time = new float[n]; val = new float[n][3]; interp = new int[n];
            for (int i = 0; i < n; i++) {
                JsonArray r = a.get(i).getAsJsonArray();
                time[i] = r.get(0).getAsFloat();
                for (int k = 0; k < 3; k++) val[i][k] = r.get(k + 1).getAsFloat();
                interp[i] = r.get(4).getAsInt();
            }
        }

        void sample(float t, float[] out, int at) {
            int n = time.length;
            if (t <= time[0] || n == 1) { copy(val[0], out, at); return; }
            if (t >= time[n - 1]) { copy(val[n - 1], out, at); return; }
            int i = 0;
            while (time[i + 1] < t) i++;
            float u = (t - time[i]) / Math.max(1e-5f, time[i + 1] - time[i]);
            float[] a = val[i], b = val[i + 1];
            if (interp[i] == 2) { copy(a, out, at); return; }
            if (interp[i] == 1 || interp[i + 1] == 1) {
                float[] p0 = val[Math.max(0, i - 1)], p3 = val[Math.min(n - 1, i + 2)];
                for (int k = 0; k < 3; k++)
                    out[at + k] = 0.5f * (2 * a[k] + (-p0[k] + b[k]) * u + (2 * p0[k] - 5 * a[k] + 4 * b[k] - p3[k]) * u * u
                            + (-p0[k] + 3 * a[k] - 3 * b[k] + p3[k]) * u * u * u);
                return;
            }
            for (int k = 0; k < 3; k++) out[at + k] = a[k] + (b[k] - a[k]) * u;
        }

        private static void copy(float[] v, float[] out, int at) { out[at] = v[0]; out[at + 1] = v[1]; out[at + 2] = v[2]; }
    }
}
