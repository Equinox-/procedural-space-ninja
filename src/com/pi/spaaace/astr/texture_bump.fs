#version 330 core

float snoise(vec2 pos);
vec3 normalHere();

out vec4 fragColor;

void main() {
	vec3 norm = normalHere();
	vec2 grad = norm.yz;
	norm.y = norm.y / 2 + .5;
	norm.z = norm.z / 2 + .5;
	fragColor = vec4(norm, length(grad));
}
