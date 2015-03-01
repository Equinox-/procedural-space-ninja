#version 330 core

float snoise(vec3 pos);

out vec4 fragColor;

uniform int octaves;
uniform float sampleRadius;
uniform float baseScale;
uniform float baseFrequency;
uniform float scaleMult;
uniform float frequencyMult;
uniform float maxAsteroidScale;
uniform vec3 seed;

uniform vec2 size;

vec3 posAtRaw(vec2 tex, float rad);

void main() {
	float y = 0;
	int i, j;
	float sc = baseScale;
	float freq = baseFrequency;
	vec2 texCoord = gl_FragCoord.xy / size;
	vec3 pos = posAtRaw(texCoord, sampleRadius);
	for (i = 0; i < octaves; i++) {
		float nValue = snoise(seed + pos * freq);
		y += nValue * sc;
		sc *= scaleMult;
		freq *= frequencyMult;
	}

	float scaling;
	int span = 2;
	float maxDelta = 0;
	float scump = 0;

	float quadHitFreq = 10;

	for (scaling = 5; scaling <= maxAsteroidScale; scaling += 5) {
		for (i = -span; i <= span; i++) {
			for (j = -span; j <= span; j++) {
				vec2 quadHit = (round(texCoord * scaling) + vec2(i, j))
						/ scaling;
				vec3 vevHit = posAtRaw(quadHit, sampleRadius);
				quadHit += vec2(snoise(seed + vevHit.xyz * quadHitFreq),
						snoise(seed + vevHit.yxz * quadHitFreq)) / scaling;
				vevHit = posAtRaw(quadHit, sampleRadius);
				float actCosine = cos(
						min(.5, abs(quadHit.y - .5)) * 2);
				vec3 mag = vevHit - pos;
				float dist = length(mag);
				float basis = abs(actCosine)
						* (snoise(seed + .01 * vevHit) * .25 + 2.5);

				if (basis > 1 && dist <= basis / scaling) {
					float val = pow(scaling * dist / basis, 2);
					if (val > .75) {
						scump = max(scump, pow(1 - val, 2));
					} else {
						maxDelta = max(maxDelta,
								sqrt(.75 - val) * basis * sampleRadius
										/ scaling);
					}
				}
			}
		}
	}
	y -= maxDelta;
	y += scump;

	fragColor = vec4(y, 0, 0, 0);
}
