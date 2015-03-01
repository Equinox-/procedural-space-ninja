#version 330 core

uniform sampler2D data;
vec3 posAtRaw(vec2 tex, float tR);

uniform vec2 size;
uniform vec2 noiseParams;

out vec4 fragColor;

vec3 posAtOff(int xo, int yo) {
	vec2 texCoord = (gl_FragCoord.xy + vec2(xo, yo)) / size;
	float radius = texture2D(data, texCoord).x;
	return posAtRaw(texCoord, noiseParams.x + noiseParams.y * radius);
}

void main() {
	vec3 here = posAtOff(0, 0);
	vec3 a = normalize(cross(posAtOff(0, -1) - here, posAtOff(-1, 0) - here));
	vec3 b = normalize(cross(posAtOff(0, 1) - here, posAtOff(1, 0) - here));
	fragColor = vec4(texture2D(data, gl_FragCoord.xy / size).x,
			(a + b) * 0.5);
}
