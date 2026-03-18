#version 330 core

layout (location = 0) in vec3 aPos;

out vec3 WorldPos;

uniform mat4 projection, view;

void main()
{
    WorldPos = aPos;
    gl_Position = projection * view * vec4(WorldPos, 1.0);
}