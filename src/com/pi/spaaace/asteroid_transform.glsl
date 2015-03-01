#version 330 core

#define M_PI 3.14159265358979323846

vec3 posAtRaw(vec2 gridTex, float rad) {
	float th = gridTex.x * 2 * M_PI;
	float psi = (gridTex.y - .5) * M_PI;
	float cPre = rad * cos(psi);
	return vec3(cPre * cos(th), cPre * sin(th), rad * sin(psi));
}