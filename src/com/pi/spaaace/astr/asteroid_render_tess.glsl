// GL_TESS_CONTROL_SHADER
#version 410 core                                                                 

#define M_PI (3.14159265)                                                                       

layout(vertices = 3) out;
in vec3 vPosition[];

out vec3 tcPosition[];

uniform vec3 eye;
uniform vec2 noiseParams;
uniform vec2 screenSize;
uniform sampler2D data;
uniform float lodBias;

uniform mat4 normalMatrix;
uniform mat4 modelMatrix;
uniform mat4 PCM;

#define NDC_EDGE (1.3)
#define FACE_AWAY_TOL (-.25)

vec4 sampleAt(vec3 dir) {
	vec2 gridTex = vec2((atan(dir.y, dir.x) + M_PI) / (2 * M_PI),
			acos(dir.z) / M_PI);
	return texture(data, gridTex);
}

vec4 project(vec3 pos) {
	vec4 projected = PCM * vec4(pos, 1);
	projected /= projected.w;
	return projected;
}

bool ndcOffScreen(vec4 ndc) {
	return ndc.z < -0.5 || any(lessThan(ndc.xy, vec2(-NDC_EDGE)))
			|| any(greaterThan(ndc.xy, vec2(NDC_EDGE)));
}

bool facesAway(vec3 model, vec3 norm) {
	return dot(eye - (modelMatrix * vec4(model, 1)).xyz,
			(normalMatrix * vec4(norm, 1)).xyz) < FACE_AWAY_TOL;
}

vec2 ndcToScreen(vec4 ndc) {
	return (clamp(ndc.xy, -NDC_EDGE, NDC_EDGE) + 1) * screenSize * 0.5;
}

float level(vec2 v0, vec3 n0, vec2 v1, vec3 n1) {
	return clamp(lodBias * (distance(v0, v1) - dot(n0, n1)) / 5, 1, 64);
}

void main() {
	tcPosition[gl_InvocationID] = vPosition[gl_InvocationID];

	if (gl_InvocationID == 0) {
		vec4 d0 = sampleAt(vPosition[0]);
		vec4 d1 = sampleAt(vPosition[1]);
		vec4 d2 = sampleAt(vPosition[2]);

		vec3 m0 = vPosition[0] * (noiseParams.x + d0.x * noiseParams.y);
		vec3 m1 = vPosition[1] * (noiseParams.x + d1.x * noiseParams.y);
		vec3 m2 = vPosition[2] * (noiseParams.x + d2.x * noiseParams.y);

		gl_TessLevelInner[0] = 0;
		gl_TessLevelOuter[0] = 0;
		gl_TessLevelOuter[1] = 0;
		gl_TessLevelOuter[2] = 0;

		if (!facesAway(m0, d0.yzw) || !facesAway(m1, d1.yzw)
				|| !facesAway(m2, d2.yzw)) {
			vec4 v0 = project(m0);
			vec4 v1 = project(m1);
			vec4 v2 = project(m2);

			if (!ndcOffScreen(v0) || !ndcOffScreen(v1) || !ndcOffScreen(v2)) {
				vec2 ss0 = ndcToScreen(v0);
				vec2 ss1 = ndcToScreen(v1);
				vec2 ss2 = ndcToScreen(v2);

				float e0 = level(ss1, d1.yzw, ss2, d2.yzw);
				float e1 = level(ss2, d2.yzw, ss0, d0.yzw);
				float e2 = level(ss0, d0.yzw, ss1, d1.yzw);

				gl_TessLevelInner[0] = e0 * .33 + e1 * .33 + e2 * .33;
				gl_TessLevelOuter[0] = e0;
				gl_TessLevelOuter[1] = e1;
				gl_TessLevelOuter[2] = e2;

			}
		}
	}
}

//GL_TESS_EVALUATION_SHADER
#version 410 core                                                                               

#define M_PI (3.14159265)

layout(triangles, fractional_odd_spacing, cw) in;
in vec3 tcPosition[];

uniform vec2 noiseParams;
uniform sampler2D data;

uniform mat4 modelMatrix;
uniform mat4 normalMatrix;
uniform mat4 PCM;

uniform sampler2D bump;

out vec3 position;
out vec3 normal, tangent, binormal;
out vec2 textureCoord;

float snoise(vec3 v);

float turbulence(vec3 v) {
	int i;
	float val = 0;
	float pd = 1;
	for (i = 0; i < 3; i++) {
		val += snoise(v * pd) / pd;
		pd *= 1;
	}
	return val;
}

void main() {
	vec3 dir = normalize(
			(gl_TessCoord.x * tcPosition[0]) + (gl_TessCoord.y * tcPosition[1])
					+ (gl_TessCoord.z * tcPosition[2]));

	vec2 gridTex = vec2((atan(dir.y, dir.x) + M_PI) / (2 * M_PI),
			acos(dir.z) / M_PI);

	vec4 texel = texture(data, gridTex.xy);
	normal = normalize((normalMatrix * vec4(texel.yzw, 1)).xyz);
	tangent = cross(normal, vec3(-0.08122501, -0.39800257, 0.9137814));
	binormal = cross(normal, tangent);

	float radius = noiseParams.x + texel.x * noiseParams.y;
	gridTex.x = mod(gridTex.x + 10, 1);
	vec2 effGrid = abs(
			vec2(cos(gridTex.x * M_PI) * cos((gridTex.y - .5) * M_PI),
					gridTex.y)) * 5;

	textureCoord = vec2(sin(effGrid.x + turbulence(dir)),
			cos(effGrid.y + turbulence(dir))) * radius;

	radius += texture2D(bump, textureCoord).w / 100;

	vec4 realPos = vec4(dir * radius, 1);
	position = (modelMatrix * realPos).xyz;

	gl_Position = PCM * realPos;
}
