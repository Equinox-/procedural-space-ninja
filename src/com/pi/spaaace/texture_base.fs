#version 330 core

float snoise(vec2 pos);

vec3 normalHere() {
	float freq = .025;
	float scale = 1.25;

	float v1 = scale
			* snoise((gl_FragCoord.xy + .12345) * vec2(.1234, .12302) * freq);
	float v2 = scale
			* snoise((gl_FragCoord.yz - .12345) * vec2(.32483, .351293) * freq);
	v1 = sign(v1) * scale - v1;
	v2 = sign(v2) * scale - v2;
	return normalize(vec3(25, v1, v2));
}