#version 330

// Shared vertex shader of the Lumora GUI pipelines (same uniforms as minecraft:core/gui).
// Besides the color it provides
//   screenUv : 0..1 across the framebuffer (from clip space)
//   localUv  : 0..1 across the drawn quad, derived from the corner index. GuiGraphicsExtractor#fill emits the
//              corners as (x0,y0) (x0,y1) (x1,y1) (x1,y0); every GUI element contributes exactly 4 vertices.
//   aspect   : framebuffer width / height (from the orthographic projection)

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;

out vec4 vertexColor;
out vec2 screenUv;
out vec2 localUv;
out float aspect;

void main() {
    vec4 pos = ProjMat * ModelViewMat * vec4(Position, 1.0);
    gl_Position = pos;
    vertexColor = Color;
    screenUv = pos.xy / pos.w * 0.5 + 0.5;
    int corner = gl_VertexID % 4;
    localUv = vec2(corner >= 2 ? 1.0 : 0.0, (corner == 1 || corner == 2) ? 1.0 : 0.0);
    aspect = abs(ProjMat[1][1] / ProjMat[0][0]);
}
