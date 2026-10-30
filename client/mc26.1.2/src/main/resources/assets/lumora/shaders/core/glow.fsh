#version 330

// Soft glow / drop shadow around a rounded rectangle. The quad is drawn larger than the element;
// the alpha channel of the vertex color carries the margin as a fraction of the quad height.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 screenUv;
in vec2 localUv;
in float aspect;

out vec4 fragColor;

float sdRoundBox(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec2 sizePx = vec2(1.0 / max(abs(dFdx(localUv.x)), 1e-5), 1.0 / max(abs(dFdy(localUv.y)), 1e-5));
    float margin = vertexColor.a * sizePx.y;
    vec2 halfInner = sizePx * 0.5 - margin;
    vec2 p = (localUv - 0.5) * sizePx;
    float radius = clamp(min(halfInner.x, halfInner.y) * 0.44, 3.0, 18.0);
    float d = sdRoundBox(p, halfInner, radius);
    // filled soft glow: full strength inside, exponential falloff outside
    float glow = exp(-max(d, 0.0) / max(margin * 0.38, 1.0));
    fragColor = vec4(vertexColor.rgb, glow * 0.55) * ColorModulator;
}
