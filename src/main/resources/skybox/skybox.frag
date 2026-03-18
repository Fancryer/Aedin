#version 330 core // skybox/skybox.frag
out vec4 FragColor;

in vec3 TexCoords;

uniform samplerCube skybox;
uniform vec3 sunColor;
uniform float time;

void main()
{
    vec4 texColor = texture(skybox, TexCoords);

    // Затемнение в зависимости от высоты солнца
    float brightness = max(sunColor.r, max(sunColor.g, sunColor.b));
    brightness = max(brightness, 0.05f);  // никогда не уходит в полный 0
    texColor.rgb *= brightness;

    FragColor = texColor;
}