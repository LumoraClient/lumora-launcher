#version 330

// Animated sky overlay for Lumora menus: aurora ribbons, twinkling stars and a vignette.
// Drawn as one full-screen quad on top of the landscape background.
// Inputs packed into the vertex color (no extra uniforms needed, works without a loaded world):
//   R,G : time in 1/100 s (16 bit, wraps after ~11 minutes)
//   B   : mode  0 = aurora + stars, 255 = vignette only
//   A   : overall strength

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

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        v += amp * noise(p);
        p *= 2.03;
        amp *= 0.5;
    }
    return v;
}

void main() {
    vec3 enc = floor(vertexColor.rgb * 255.0 + 0.5);
    float t = (enc.r * 256.0 + enc.g) / 100.0;
    float strength = vertexColor.a;
    vec2 uv = screenUv;               // y = 1 at the top
    vec2 p = vec2(uv.x * aspect, uv.y);

    if (enc.b > 127.0) {
        // vignette: darken edges and the bottom for text contrast
        float d = length((uv - vec2(0.55, 0.55)) * vec2(aspect, 1.0) * 0.9);
        float v = smoothstep(0.45, 1.25, d) * 0.75 + smoothstep(0.35, 0.0, uv.y) * 0.35;
        fragColor = vec4(0.02, 0.03, 0.05, clamp(v, 0.0, 0.85) * strength) * ColorModulator;
        return;
    }

    // aurora: three soft ribbons with vertical curtain streaks
    vec3 col = vec3(0.0);
    float acc = 0.0;
    vec3 tint[3] = vec3[3](vec3(0.55, 0.36, 0.96), vec3(0.86, 0.42, 0.95), vec3(0.40, 0.48, 1.00));
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float center = 0.74 + 0.06 * fi
                + 0.07 * sin(p.x * (1.6 + 0.4 * fi) + t * (0.10 + 0.03 * fi) + fi * 2.1)
                + 0.06 * (fbm(vec2(p.x * 1.4 + t * 0.04, fi * 3.7)) - 0.5);
        float width = 0.035 + 0.02 * fbm(vec2(p.x * 2.0 - t * 0.05, fi));
        float band = exp(-pow((uv.y - center) / width, 2.0));
        float above = smoothstep(center, center + 0.22, uv.y) * (1.0 - smoothstep(center + 0.22, center + 0.4, uv.y));
        float curtain = 0.55 + 0.45 * fbm(vec2(p.x * 28.0 + t * 0.35 + fi * 10.0, uv.y * 1.5));
        float shimmer = 0.75 + 0.25 * sin(t * 0.7 + p.x * 5.0 + fi);
        float a = (band * 0.9 + above * 0.25) * curtain * shimmer * (0.55 - 0.12 * fi);
        col += tint[i] * a;
        acc += a;
    }

    // twinkling stars in the upper part of the sky
    vec2 grid = floor(p * 140.0);
    float h = hash(grid);
    float star = 0.0;
    if (h > 0.986 && uv.y > 0.45) {
        vec2 f = fract(p * 140.0) - 0.5;
        float tw = pow(0.5 + 0.5 * sin(t * (1.5 + h * 3.0) + h * 80.0), 8.0);
        star = tw * smoothstep(0.35, 0.0, length(f)) * smoothstep(0.45, 0.75, uv.y);
    }

    vec3 color = col + vec3(0.85, 0.95, 1.0) * star;
    float alpha = clamp(acc * 0.85 + star * 0.9, 0.0, 0.9) * strength;
    fragColor = vec4(color / max(acc + star, 0.001), alpha) * ColorModulator;
}
