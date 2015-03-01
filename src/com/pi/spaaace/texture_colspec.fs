#version 330 core

float snoise(vec2 pos);
vec3 normalHere();

out vec4 fragColor;

void main() {
	vec3 norm = normalHere();

	vec3 normalColor = vec3(.55, .5, .45);
	float noisy = snoise(
			(gl_FragCoord.xy + .12345) * vec2(.123124, .1238102) * .75);
	float specNoise = snoise(
			(gl_FragCoord.xy + .12345) * vec2(.123124, .1238102) * .01);
	specNoise = pow(specNoise, 16);
	vec3 color = mix(normalColor, vec3(.4, .4, .4),
			abs(noisy) * (norm.y * norm.y + norm.z * norm.z));
	fragColor = vec4(color, specNoise);
}
