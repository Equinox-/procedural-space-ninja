package com.pi.spaaace;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import com.pi.core.wind.GLWindow;
import com.pi.math.matrix.Matrix4;
import com.pi.math.vector.VectorND;
import com.pi.spaaace.astr.Asteroid;
import com.pi.user.camera.Camera;
import com.pi.user.camera.Camera3rdPerson;

public class SpaceMain extends GLWindow {
	private Camera cam;
	private Matrix4 project = Matrix4.identity();

	private List<Asteroid> asteroids;

	@Override
	public void init() {
		setSize(1280, 720);
		setTitle("SpaceMain!");
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_CULL_FACE);

		asteroids = new ArrayList<>();
		for (int i = 0; i < 1; i++) {
			final float scalerand = .5f;
			float posrand =0* 50;
			float speedRand = 0*50;
			final float scaleRand2 = 5 * (float) Math.pow(Math.random(), 2);
			Asteroid tmp = null;
			prim: for (int t = 0; t < 25; t++) {
				tmp = new Asteroid(this, new VectorND(posrand
						* (float) (Math.random() - .5), posrand
						* (float) (Math.random() - .5), posrand
						* (float) (Math.random() - .5)), new VectorND(
						(float) Math.random() * speedRand,
						(float) Math.random() * speedRand,
						(float) Math.random() * speedRand), new VectorND(.25f
						+ scaleRand2 + scalerand * (float) Math.random(), .25f
						+ scaleRand2 + scalerand * (float) Math.random(), .25f
						+ scaleRand2 + scalerand * (float) Math.random()));

				for (Asteroid a : asteroids) {
					if (a.getRoot().dist(tmp.getRoot()) < (a.maxRadius() + tmp
							.maxRadius())) {
						posrand *= 1.1;
						if (t == 24)
							System.out.println("Failed ;(");
						continue prim;
					}
				}
				break;
			}
			asteroids.add(tmp);
			tmp.generate();
			System.out.println("Generated " + i);
		}

		cam = new Camera3rdPerson(this, 5).rates(2, 2, 5);
	}

	@Override
	public void render() {
		Asteroid.wireframe = getEvents().isKeyDown(GLFW.GLFW_KEY_SPACE);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glViewport(0, 0, getEvents().getWidth(), getEvents().getHeight());
		for (Asteroid asteroid : asteroids)
			asteroid.render(project, cam.position());

	}

	long lastFPS = 0;
	int frames = 0;

	@Override
	public void update() {
		final float tanV = (float) Math.tan(120 * Math.PI / 360.0);
		final float aspect = getEvents().getHeight()
				/ (float) getEvents().getWidth();
		float near = .1f;
		project.setPerspective(-tanV * near, tanV * near,
				-tanV * aspect * near, tanV * aspect * near, near, near + 1000);

		cam.update();
		project = cam.apply(project);

		frames++;
		if (System.currentTimeMillis() > lastFPS + 3000) {
			System.out
					.println("Current FPS: "
							+ (frames * 1000.0f / (System.currentTimeMillis() - lastFPS)));
			lastFPS = System.currentTimeMillis();
			frames = 0;
		}
	}

	@Override
	public void dispose() {
		// Do nothing
	}

	public static void main(String[] args) {
		new SpaceMain().start();
	}
}
