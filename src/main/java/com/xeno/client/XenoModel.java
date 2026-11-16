package com.xeno.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Carga xeno_model.json (tu glTF convertido) y lo dibuja con sus animaciones: sin librerías externas. */
public final class XenoModel {
	private record Track(int node, char path, float[] k, float[] v) {}
	private record Anim(float len, Track[] tracks) {}

	private static boolean loaded;
	private static int n;
	private static int[] parent, mesh;
	private static float[][] baseT, baseR, cubeV;
	private static int[][] cubeI;
	private static float preDy, preDz;
	private static final Map<String, Anim> ANIMS = new HashMap<>();
	private static Vector3f[] tr;
	private static Quaternionf[] rot;
	private static Matrix4f[] world;
	private static boolean[] done;
	private static final Matrix4f tmp = new Matrix4f();
	private static final Vector3f nrm = new Vector3f();
	private static final Quaternionf q1 = new Quaternionf(), q2 = new Quaternionf();

	private static float[] f(JsonArray a) { float[] r = new float[a.size()]; for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsFloat(); return r; }
	private static int[] ints(JsonArray a) { int[] r = new int[a.size()]; for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsInt(); return r; }

	private static synchronized void load() {
		if (loaded) return;
		try (var in = XenoModel.class.getResourceAsStream("/xeno_model.json"); var rd = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			JsonObject o = JsonParser.parseReader(rd).getAsJsonObject();
			preDy = o.getAsJsonObject("pre").get("dy").getAsFloat();
			preDz = o.getAsJsonObject("pre").get("dz").getAsFloat();
			JsonArray nodes = o.getAsJsonArray("nodes");
			n = nodes.size();
			parent = new int[n]; mesh = new int[n]; baseT = new float[n][]; baseR = new float[n][];
			tr = new Vector3f[n]; rot = new Quaternionf[n]; world = new Matrix4f[n]; done = new boolean[n];
			for (int i = 0; i < n; i++) {
				JsonObject nd = nodes.get(i).getAsJsonObject();
				parent[i] = nd.get("p").getAsInt();
				baseT[i] = f(nd.getAsJsonArray("t")); baseR[i] = f(nd.getAsJsonArray("r"));
				mesh[i] = nd.has("m") ? nd.get("m").getAsInt() : -1;
				tr[i] = new Vector3f(); rot[i] = new Quaternionf(); world[i] = new Matrix4f();
			}
			JsonArray cubes = o.getAsJsonArray("cubes");
			cubeV = new float[cubes.size()][]; cubeI = new int[cubes.size()][];
			for (int i = 0; i < cubes.size(); i++) {
				JsonObject c = cubes.get(i).getAsJsonObject();
				cubeV[i] = f(c.getAsJsonArray("v")); cubeI[i] = ints(c.getAsJsonArray("i"));
			}
			for (var e : o.getAsJsonObject("anims").entrySet()) {
				JsonObject a = e.getValue().getAsJsonObject();
				JsonArray ts = a.getAsJsonArray("tr");
				Track[] tracks = new Track[ts.size()];
				for (int i = 0; i < tracks.length; i++) {
					JsonObject t = ts.get(i).getAsJsonObject();
					tracks[i] = new Track(t.get("n").getAsInt(), t.get("p").getAsString().charAt(0), f(t.getAsJsonArray("k")), f(t.getAsJsonArray("v")));
				}
				ANIMS.put(e.getKey(), new Anim(a.get("len").getAsFloat(), tracks));
			}
		} catch (Exception ex) { throw new RuntimeException("No se pudo cargar xeno_model.json", ex); }
		loaded = true;
	}

	private static void sample(Track t, float time) {
		float[] k = t.k();
		int c = k.length, i = 0, j = 0;
		float a = 0;
		if (c > 1) {
			while (i < c - 2 && time >= k[i + 1]) i++;
			j = i + 1;
			a = k[j] > k[i] ? Math.max(0f, Math.min(1f, (time - k[i]) / (k[j] - k[i]))) : 0f;
		}
		float[] v = t.v();
		if (t.path() == 't') {
			tr[t.node()].set(v[i * 3] + (v[j * 3] - v[i * 3]) * a, v[i * 3 + 1] + (v[j * 3 + 1] - v[i * 3 + 1]) * a, v[i * 3 + 2] + (v[j * 3 + 2] - v[i * 3 + 2]) * a);
		} else if (t.path() == 'r') {
			q1.set(v[i * 4], v[i * 4 + 1], v[i * 4 + 2], v[i * 4 + 3]);
			q2.set(v[j * 4], v[j * 4 + 1], v[j * 4 + 2], v[j * 4 + 3]);
			rot[t.node()].set(q1.slerp(q2, a));
		}
	}

	private static void solve(int i) {
		if (done[i]) return;
		if (parent[i] >= 0) { solve(parent[i]); world[i].set(world[parent[i]]); }
		else world[i].identity();
		world[i].translate(tr[i]).rotate(rot[i]);
		done[i] = true;
	}

	public static void render(String anim, float timeSec, MatrixStack.Entry entry, VertexConsumer vc, int light, int overlay) {
		load();
		for (int i = 0; i < n; i++) {
			tr[i].set(baseT[i][0], baseT[i][1], baseT[i][2]);
			rot[i].set(baseR[i][0], baseR[i][1], baseR[i][2], baseR[i][3]);
			done[i] = false;
		}
		Anim a = ANIMS.get(anim);
		if (a != null) {
			float t = a.len() > 0 ? timeSec % a.len() : 0f;
			if (t < 0) t += a.len();
			for (Track tk : a.tracks()) sample(tk, t);
		}
		for (int i = 0; i < n; i++) solve(i);
		Matrix4f base = new Matrix4f(entry.getPositionMatrix()).translate(0f, preDy, preDz);
		for (int i = 0; i < n; i++) {
			if (mesh[i] < 0) continue;
			float[] v = cubeV[mesh[i]];
			int[] idx = cubeI[mesh[i]];
			tmp.set(base).mul(world[i]);
			for (int k = 0; k + 2 < idx.length; k += 3) {
				for (int j = 0; j < 4; j++) {          // triángulo emitido como quad degenerado
					int o = idx[k + Math.min(j, 2)] * 8;
					nrm.set(v[o + 3], v[o + 4], v[o + 5]);
					world[i].transformDirection(nrm);
					vc.vertex(tmp, v[o], v[o + 1], v[o + 2]).color(255, 255, 255, 255).texture(v[o + 6], v[o + 7])
						.overlay(overlay).light(light).normal(entry, nrm.x, nrm.y, nrm.z);
				}
			}
		}
	}
}
