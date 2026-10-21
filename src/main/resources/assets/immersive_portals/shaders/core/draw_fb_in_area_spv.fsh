#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

uniform sampler2D InSampler;

layout(location = 0) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

// samples the framebuffer at the same screen position (MC 26.3+ shader dialect)
void main() {
    fragColor = texture(InSampler, gl_FragCoord.xy / ScreenSize);
}
