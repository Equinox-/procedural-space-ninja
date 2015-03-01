package com.pi.spaaace;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;

import com.pi.core.glsl.ShaderOnTexture;
import com.pi.core.glsl.ShaderProgram;
import com.pi.core.model.Model;
import com.pi.core.model.PrimitiveType;
import com.pi.core.texture.DataTexture;
import com.pi.core.texture.Texture;
import com.pi.core.texture.TextureFilter;
import com.pi.core.texture.TextureWrap;
import com.pi.core.vertex.VertexData;
import com.pi.core.vertex.VertexTypes.Vertex3D;
import com.pi.math.matrix.Matrix4;
import com.pi.math.vector.Vector;
import com.pi.math.vector.VectorND;

public class Asteroid {
	private static final int AS_X = 1024, AS_Y = 1024;
	private static final int TEXTURE_DIM = 256;
	private static final int SOFTWARE_LOD = 7;

	private static ShaderProgram noiseGen = null;
	private static ShaderProgram normals = null;
	private static ShaderProgram asteroidRender = null;
	private static ShaderProgram asteroidRenderWireframe = null;
	private static ShaderProgram bumpGen = null;
	private static ShaderProgram colspecGen = null;
	private static ShaderOnTexture noiseTmp = null;
	private static Model<Vertex3D> icosahedron;
	private static Texture textureBump, textureColSpec;

	private Vector noiseParams;
	private ShaderOnTexture noise;
	private Matrix4 model = Matrix4.identity(), normal = Matrix4.identity();

	private float maxDisplacement;

	private Vector root, spin, scale;
	private float angMomen;

	private static InputStream csr(String s) {
		return SpaceMain.class.getResourceAsStream(s);
	}

	private static int subAddr(List<Vector> pts, Map<String, Integer> assoc,
			int a, int b) {
		String key = a < b ? (a + "_" + b) : (b + "_" + a);
		Integer val = assoc.get(key);
		if (val != null)
			return val.intValue();
		pts.add(pts.get(a).clone().add(pts.get(b)).normalize());
		assoc.put(key, pts.size() - 1);
		return pts.size() - 1;
	}

	private static int[] subdivide(List<Vector> pts, int[] index) {
		Map<String, Integer> assoc = new HashMap<>();
		int[] res = new int[index.length * 4];
		int rI = 0;
		for (int i = 0; i < index.length; i += 3) {
			int v1 = index[i];
			int v2 = index[i + 1];
			int v3 = index[i + 2];
			int v12 = subAddr(pts, assoc, v1, v2);
			int v23 = subAddr(pts, assoc, v2, v3);
			int v31 = subAddr(pts, assoc, v3, v1);

			res[rI++] = v1;
			res[rI++] = v12;
			res[rI++] = v31;

			res[rI++] = v2;
			res[rI++] = v23;
			res[rI++] = v12;

			res[rI++] = v3;
			res[rI++] = v31;
			res[rI++] = v23;

			res[rI++] = v12;
			res[rI++] = v23;
			res[rI++] = v31;
		}
		return res;
	}

	private static void prepare() {
		if (bumpGen == null)
			bumpGen = new ShaderProgram().fragment(csr("texture_bump.fs"))
					.fragment(csr("texture_base.fs"))
					.fragment(csr("noise2D.glsl"))
					.vertex(csr("passthrough.vs")).link();
		if (colspecGen == null)
			colspecGen = new ShaderProgram()
					.fragment(csr("texture_colspec.fs"))
					.fragment(csr("texture_base.fs"))
					.fragment(csr("noise2D.glsl"))
					.vertex(csr("passthrough.vs")).link();
		if (noiseGen == null) {
			noiseGen = new ShaderProgram().fragment(csr("noise3D.glsl"))
					.fragment(csr("asteroid_transform.glsl"))
					.fragment(csr("noise.fs")).vertex(csr("passthrough.vs"))
					.link();
			noiseGen.bind();
			noiseGen.uniform("size").vector(AS_X, AS_Y);
		}

		if (textureBump == null) {
			ShaderOnTexture target = new ShaderOnTexture(
					textureBump = new Texture(TEXTURE_DIM, TEXTURE_DIM,
							GL11.GL_RGBA).filter(null, TextureFilter.LINEAR,
							TextureFilter.LINEAR).wrap(
							TextureWrap.MIRRORED_REPEAT,
							TextureWrap.MIRRORED_REPEAT));
			target.gpuAlloc();
			target.render(bumpGen);
			target.getFBO().gpuFree();
		}

		if (textureColSpec == null) {
			ShaderOnTexture colspecTarget = new ShaderOnTexture(
					textureColSpec = new Texture(TEXTURE_DIM, TEXTURE_DIM,
							GL11.GL_RGBA).filter(null, TextureFilter.LINEAR,
							TextureFilter.LINEAR).wrap(
							TextureWrap.MIRRORED_REPEAT,
							TextureWrap.MIRRORED_REPEAT));
			colspecTarget.gpuAlloc();
			colspecTarget.render(colspecGen);
			colspecTarget.getFBO().gpuFree();
		}

		if (noiseTmp == null) {
			noiseTmp = new ShaderOnTexture(new DataTexture(1, AS_X, AS_Y)
					.filter(null, TextureFilter.LINEAR, TextureFilter.LINEAR)
					.wrap(TextureWrap.REPEAT, TextureWrap.CLAMP_TO_EDGE));
			noiseTmp.gpuAlloc();
		}
		if (normals == null) {
			normals = new ShaderProgram().fragment(csr("normals.fs"))
					.fragment(csr("asteroid_transform.glsl"))
					.vertex(csr("passthrough.vs")).link();
			normals.bind();
			normals.uniform("data").texture(noiseTmp.getResult());
			normals.uniform("size").vector(AS_X, AS_Y);
		}
		if (asteroidRender == null) {
			asteroidRender = new ShaderProgram()
					.vertex(csr("asteroid_transform.glsl"))
					.joined(csr("asteroid_render.glsl"))
					.attach(GL40.GL_TESS_EVALUATION_SHADER, csr("noise3D.glsl"))
					.joined(csr("asteroid_render_tess.glsl")).link();
			asteroidRender.bind();
			asteroidRender.uniform("bump").texture(textureBump);
			asteroidRender.uniform("colspec").texture(textureColSpec);
		}
		if (asteroidRenderWireframe == null) {
			asteroidRenderWireframe = new ShaderProgram()
					.vertex(csr("asteroid_transform.glsl"))
					.joined(csr("asteroid_render.glsl"))
					.joined(csr("asteroid_render_tess.glsl"))
					.attach(GL32.GL_GEOMETRY_SHADER, csr("wireframe.geom"))
					.attach(GL40.GL_TESS_EVALUATION_SHADER, csr("noise3D.glsl"))
					.link();
			asteroidRenderWireframe.bind();
			asteroidRenderWireframe.uniform("bump").texture(textureBump);
			asteroidRenderWireframe.uniform("colspec").texture(textureColSpec);
		}
		if (icosahedron == null) {
			final float X = 0.525731112119133606f;
			final float Z = 0.850650808352039932f;
			float[][] data = new float[][] { { -X, 0, Z }, { X, 0, Z },
					{ -X, 0, -Z }, { X, 0, -Z }, { 0, Z, X }, { 0, Z, -X },
					{ 0, -Z, X }, { 0, -Z, -X }, { Z, X, 0 }, { -Z, X, 0 },
					{ Z, -X, 0 }, { -Z, -X, 0 } };
			List<Vector> base = new ArrayList<>(12);
			for (float[] f : data)
				base.add(new VectorND(f));

			int[][] index = new int[SOFTWARE_LOD][];
			index[0] = new int[] { 0, 4, 1, 0, 9, 4, 9, 5, 4, 4, 5, 8, 4, 8, 1,
					8, 10, 1, 8, 3, 10, 5, 3, 8, 5, 2, 3, 2, 7, 3, 7, 10, 3, 7,
					6, 10, 7, 11, 6, 11, 0, 6, 0, 1, 6, 6, 1, 10, 9, 0, 11, 9,
					11, 2, 9, 2, 5, 7, 2, 11 };
			for (int i = 1; i < SOFTWARE_LOD; i++) {
				index[i] = subdivide(base, index[i - 1]);
				System.out.println("LOD " + i + " has " + index[i].length / 3
						+ " triangles");
			}

			VertexData<Vertex3D> vD = new VertexData<>(Vertex3D.class,
					base.size());
			for (int i = 0; i < base.size(); i++)
				vD.vertexDB[i].pos.set(base.get(i));
			icosahedron = new Model<>(PrimitiveType.TRIANGLE_PATCHES, vD, index);
			icosahedron.gpuAlloc();
			icosahedron.gpuUpload();
		}
	}

	public Asteroid(Vector root, Vector spin, Vector scale) {
		prepare();
		this.root = root;
		this.scale = scale;

		angMomen = spin.magnitude();
		this.spin = spin.normalize();

		this.noiseParams = new VectorND(2f + (float) Math.random() * 5,
				(.15f + (float) Math.random() * .25f) * .1f);
	}

	public void generate() {
		if (noise != null)
			return;
		noise = new ShaderOnTexture(new DataTexture(4, AS_X, AS_Y).filter(null,
				TextureFilter.LINEAR, TextureFilter.LINEAR).wrap(
				TextureWrap.REPEAT, TextureWrap.CLAMP_TO_EDGE));
		((DataTexture) noise.getResult()).cpuFree();
		noise.gpuAlloc();

		noiseGen.bind();
		int octaves = 6;
		float scaleMult = .25f;
		float frequencyMult = 2f;
		float baseScale = noiseParams.get(0) * 5f + 100;
		float baseFrequency = noiseParams.get(0) / 100;

		noiseGen.uniform("octaves").scalar(octaves);
		noiseGen.uniform("scaleMult").scalar(scaleMult);
		noiseGen.uniform("frequencyMult").scalar(frequencyMult);
		noiseGen.uniform("baseScale").scalar(baseScale);
		noiseGen.uniform("baseFrequency").scalar(baseFrequency);
		noiseGen.uniform("seed").vector(root);
		noiseGen.uniform("sampleRadius").scalar(
				noiseParams.get(0) + noiseParams.get(1));
		noiseGen.uniform("maxAsteroidScale").scalar(
				15f * (float) Math.sqrt(maxRadius()));
		noiseTmp.render(noiseGen);

		normals.bind();
		normals.uniform("data").texture(noiseTmp.getResult());
		normals.uniform("noiseParams").vector(noiseParams);
		normals.bindSamplers();
		noise.render(normals);

		maxDisplacement = 0;
		for (int i = 0; i < octaves; i++) {
			maxDisplacement += baseScale;
			baseScale *= scaleMult;
		}
	}

	public float maxRadius() {
		return Math.max(scale.get(0), Math.max(scale.get(1), scale.get(2)))
				* (noiseParams.get(0) + noiseParams.get(1) * maxDisplacement);
	}

	public static boolean wireframe = false;

	public void render(Matrix4 project, Vector eye) {
		float dist = Math.max(0, (float) Math.pow(
				eye.dist(root) / (2 + Math.sqrt(maxRadius())), .3) - 1);
		float lod = (.95f - Math.min(.95f, dist / 3.5f)) * (SOFTWARE_LOD + 1);
		int softwareLOD = Math.min(SOFTWARE_LOD - 1, (int) lod);

		ShaderProgram render = wireframe ? asteroidRenderWireframe
				: asteroidRender;

		render.bind();
		render.uniform("data").texture(noise.getResult());
		render.uniform("noiseParams").vector(noiseParams);
		float lodPartial = (lod - softwareLOD) * 2;
		/*
		 * + Math.max(1, (float) Math.sqrt(maxRadius())); System.out.println(Math.max(1, (float) Math.sqrt(maxRadius())));
		 */
		if (lod > SOFTWARE_LOD -1)
			lodPartial *= 3;
		render.uniform("lodBias").scalar(lodPartial);

		render.uniform("modelMatrix").matrix(model);
		render.uniform("normalMatrix").matrix(normal);
		model.multiplyInto(project);
		render.uniform("PCM").matrix(model);
		render.uniform("eye").vector(eye);
		render.bindSamplers();

		icosahedron.render(softwareLOD);

		model.setScale(scale);
		model.makeIdentity();

		normal.makeIdentity();
		normal.setAxisAngle((float) GLFW.glfwGetTime() * angMomen / maxRadius()
				/ maxRadius(), spin);
		for (int i = 0; i < 3; i++)
			for (int q = 0; q < 12; q += 4)
				model.put(i + q, model.get(i + q) * scale.get(i));
		normal.setTranslation(root);
		model.multiplyInto(normal);

		model.invertInto(normal).transposeInPlace().makeMatrix3();
	}

	public Vector getRoot() {
		return root;
	}
}
