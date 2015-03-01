// GL_TESS_CONTROL_SHADER
#version 410 core                                                                 

#define M_PI (3.14159265)                                                                       

layout(vertices = 3) out;
in vec3 vPosition[];

out vec3 tcPosition[];

uniform sampler2D data;
uniform float lodBias;

uniform mat4 modelMatrix;
uniform mat4 normalMatrix;
uniform vec3 eye;
uniform vec2 noiseParams;

/* No need to tesselate the back */
bool doesClip(vec3 dir, vec3 pos) {
	return dot((normalMatrix * vec4(dir, 1)).xyz, normalize(eye - pos)) < 0;
}

void main() {
	tcPosition[gl_InvocationID] = vPosition[gl_InvocationID];

	if (gl_InvocationID == 0) {
		vec4 tData[3];
		vec4 avg = vec4(0);
		int i;
		vec3 dir, pos;
		vec2 gridTex;
		int clipped = 0;
		float dist = 0;
		for (i = 0; i < 3; i++) {
			dir = normalize(vPosition[i]);
			pos = (modelMatrix * vec4(dir * noiseParams.x, 1)).xyz;
			if (doesClip(dir, pos))
				clipped++;
			dist += distance(eye, pos);
			gridTex = vec2((atan(dir.y, dir.x) + M_PI) / (2 * M_PI),
					acos(dir.z) / M_PI);
			avg += (tData[i] = texture(data, gridTex));
		}
		avg /= 3;
		dist /= 3;
		avg.yzw = normalize(avg.yzw);

		float hDev = 0;
		float nDev = 0;
		for (i = 0; i < 3; i++) {
			float hError = tData[i].x - avg.x;
			float nError = 1 - dot(tData[i].yzw, avg.yzw);
			hDev += hError * hError;
			nDev += nError * nError;
		}
		hDev = 0;

		float amnt = max(1,
				(1 + nDev + hDev) * lodBias);
		/* No need to tesselate the back */
		/*if (clipped == 3)
			amnt = 1;*/
		gl_TessLevelInner[0] = amnt;
		gl_TessLevelOuter[0] = amnt;
		gl_TessLevelOuter[1] = amnt;
		gl_TessLevelOuter[2] = amnt;
	}
}

//GL_TESS_EVALUATION_SHADER
#version 410 core                                                                               

#define M_PI (3.14159265)

layout(triangles, equal_spacing, cw) in;
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
					gridTex.y));

	textureCoord = vec2(sin(5 * effGrid.x + turbulence(dir)),
			cos(5 * effGrid.y + turbulence(dir)));
	radius += texture2D(bump, textureCoord).w / 25;

	vec4 realPos = vec4(dir * radius, 1);
	position = (modelMatrix * realPos).xyz;

	gl_Position = PCM * realPos;
}
