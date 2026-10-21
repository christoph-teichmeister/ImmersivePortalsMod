#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;

in vec4 vertexColor;

out vec4 fragColor;

// samples the framebuffer at the same screen position (MC 26.1.x/26.2 shader dialect)
void main() {
    fragColor = texture(InSampler, gl_FragCoord.xy / ScreenSize);
}
