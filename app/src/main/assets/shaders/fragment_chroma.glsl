#version 100
// Chroma key (green/blue screen).
//
// Works in YUV chrominance space rather than RGB: distance is measured only on
// the U/V axes, so a subject's brightness does not affect the matte. This is
// the approach used by OBS and the widely referenced libretro/glsl-shaders
// implementation, and it holds up far better than naive RGB distance on
// unevenly lit footage.
//
// similarity  - how close a pixel must be to the key colour to be removed
// smoothness  - softness of the matte edge (avoids jagged cut-outs)
// spill       - desaturates green light bouncing onto the subject
//
// Note: MediaCodec discards alpha when encoding, so keyed pixels are composited
// against uBackColor here rather than exported as transparency.
precision mediump float;

uniform sampler2D uTexSampler;
uniform vec3 uKeyColor;
uniform vec3 uBackColor;
uniform float uSimilarity;
uniform float uSmoothness;
uniform float uSpill;

varying vec2 vTexSamplingCoord;

vec2 RGBtoUV(vec3 rgb) {
  return vec2(
    rgb.r * -0.169 + rgb.g * -0.331 + rgb.b *  0.5   + 0.5,
    rgb.r *  0.5   + rgb.g * -0.419 + rgb.b * -0.081 + 0.5
  );
}

void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);

  float chromaDist = distance(RGBtoUV(src.rgb), RGBtoUV(uKeyColor));
  float baseMask = chromaDist - uSimilarity;
  float fullMask = pow(clamp(baseMask / max(uSmoothness, 0.0001), 0.0, 1.0), 1.5);

  // Suppress colour spill on the retained subject.
  float spillVal = pow(clamp(baseMask / max(uSpill, 0.0001), 0.0, 1.0), 1.5);
  float desat = clamp(src.r * 0.2126 + src.g * 0.7152 + src.b * 0.0722, 0.0, 1.0);
  vec3 despilled = mix(vec3(desat, desat, desat), src.rgb, spillVal);

  // Composite over the chosen background, since alpha cannot survive encoding.
  gl_FragColor = vec4(mix(uBackColor, despilled, fullMask), 1.0);
}
