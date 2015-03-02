//GL_VERTEX_SHADER
#version 330 core

layout(location = 0) in vec3 inPos;
out vec3 vPosition;

vec3 posAtRaw(vec2 tex, float r);

void main() {
	vPosition = inPos;
}

//GL_FRAGMENT_SHADER
#version 330 core

out vec4 fragColor;
in vec3 normal, tangent, binormal, position;

in vec2 textureCoord;

uniform sampler2D bump, colspec;
uniform vec3 eye;

uniform float time;

void main() {
	vec3 lightDirection = vec3(1, 0, 0);
	vec3 eyeDirection = -normalize(eye - position);

	vec4 colspec = texture2D(colspec, textureCoord);
	vec3 normInfo = texture2D(bump, textureCoord).xyz;
	vec3 legitNorm = normalize(
			normInfo.x * normal + normInfo.y * tangent + normInfo.z * binormal);

	float intensity = .4;
	float light = dot(lightDirection, legitNorm);
	light = (light > 0 ? light : 0);
	intensity += .5 * light;
	intensity += 5 * light * colspec.w
			* pow(
					max(0.0,
							dot(reflect(-lightDirection, legitNorm),
									eyeDirection)), 2);
	fragColor = vec4(colspec.xyz * intensity, 1);
}
