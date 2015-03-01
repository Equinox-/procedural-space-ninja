#version 330 core

layout(triangles) in;
layout(line_strip, max_vertices = 4) out;
/*layout(triangle_strip, max_vertices = 3) out;*/

in vec3 normal[];
in vec3 position[];
uniform vec3 eye;

out vec3 gnormal;

bool doesClip(int i) {
	return false && dot(normal[i], normalize(eye - position[i])) < -.1;
}

void main()
{
	if (!doesClip(0) && !doesClip(1) && !doesClip(2)) {
		vec3 a = normalize(cross(position[0] - position[1], position[2] - position[1]));
		int i, j;
		for (i = 0; i<3; i++) {
			j = i % 3;
			gnormal = a;
		    gl_Position = gl_in[j].gl_Position;
		    
		    EmitVertex();
	    }
	    
	    EndPrimitive();
	}
}