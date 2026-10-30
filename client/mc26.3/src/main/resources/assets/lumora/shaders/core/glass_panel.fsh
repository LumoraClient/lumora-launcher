#version 330
#extension GL_ARB_separate_shader_objects : require

// Lumora "glass" panel: anti-aliased rounded rectangle (signed distance field), subtle vertical gradient,
// top highlight and a light inner border. The rectangle size in pixels is derived from screen-space
// derivatives of localUv, so no per-draw uniforms are needed.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 screenUv;
layout(location = 2) in vec2 localUv;
layout(location = 3) in float aspect;

layout(location = 0) out vec4 fragColor;

float sdRoundBox(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec2 sizePx = vec2(1.0 / max(abs(dFdx(localUv.x)), 1e-5), 1.0 / max(abs(dFdy(localUv.y)), 1e-5));
    vec2 halfSize = sizePx * 0.5;
    vec2 p = (localUv - 0.5) * sizePx;
    float radius = clamp(min(sizePx.x, sizePx.y) * 0.22, 3.0, 18.0);
    float d = sdRoundBox(p, halfSize - 0.5, radius);

    float inside = 1.0 - smoothstep(-0.75, 0.75, d);
    if (inside <= 0.0) {
        discard;
    }
    float ring = 1.0 - smoothstep(0.6, 1.8, abs(d + 1.0));
    float topLight = smoothstep(3.0, 0.0, abs(p.y + halfSize.y - 2.0)) * 0.10;

    vec3 base = vertexColor.rgb * mix(1.10, 0.90, localUv.y);
    vec3 border = mix(base, vec3(1.0), 0.20);
    vec3 color = mix(base, border, ring * 0.85) + topLight;
    float alpha = max(vertexColor.a, ring * 0.9) * inside;
    fragColor = vec4(color, alpha) * ColorModulator;
}
