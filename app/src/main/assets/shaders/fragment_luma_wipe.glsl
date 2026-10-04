#version 100
// Luma-wipe transition, the architecture used by MLT (Shotcut/Kdenlive) and
// libopenshot (OpenShot).
//
// A grayscale "luma map" assigns every pixel a value 0..1. A threshold sweeps
// from 0 to 1 over the transition; pixels whose map value is below the
// threshold have already been revealed, and `softness` feathers the boundary.
// That single mechanism produces every classic wipe shape - the shape lives in
// the map, not in the code - which is why those editors ship folders of PGM
// files rather than one shader per effect.
//
// Vireo generates the maps procedurally instead of shipping image assets, so
// the APK gains 16 wipe shapes for a few hundred bytes.
precision mediump float;

uniform sampler2D uTexSampler;
uniform float uPattern;    // which luma map to generate
uniform float uThreshold;  // 0..1 sweep position
uniform float uSoftness;   // edge feather
uniform float uInvert;     // 1.0 reverses the reveal order

varying vec2 vTexSamplingCoord;

const float PI = 3.14159265;

float hash(vec2 p) {
  return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// Value noise, for the "cloud" dissolve map.
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

float lumaMap(vec2 uv, float which) {
  vec2 c = uv - 0.5;
  float r = length(c) * 1.41421;
  float ang = atan(c.y, c.x);

  if (which < 0.5)       return uv.x;                              // linear X
  else if (which < 1.5)  return uv.y;                              // linear Y
  else if (which < 2.5)  return abs(c.x) * 2.0;                    // barn door H
  else if (which < 3.5)  return abs(c.y) * 2.0;                    // barn door V
  else if (which < 4.5)  return r;                                 // iris / radial
  else if (which < 5.5)  return max(abs(c.x), abs(c.y)) * 2.0;     // box
  else if (which < 6.5)  return (abs(c.x) + abs(c.y)) * 2.0;       // diamond
  else if (which < 7.5)  return (ang + PI) / (2.0 * PI);           // clock
  else if (which < 8.5)  return abs(ang) / PI;                     // symmetric clock
  else if (which < 9.5)  return fract((ang + PI) / (2.0 * PI) + r * 2.0); // spiral
  else if (which < 10.5) return fract(uv.y * 8.0);                 // blinds H
  else if (which < 11.5) return fract(uv.x * 8.0);                 // blinds V
  else if (which < 12.5) {                                          // checkerboard
    float cb = mod(floor(uv.x * 8.0) + floor(uv.y * 8.0), 2.0);
    return clamp(cb * 0.5 + r * 0.5, 0.0, 1.0);
  }
  else if (which < 13.5) return fract((ang + PI) / (2.0 * PI) * 8.0); // burst
  else if (which < 14.5) return noise(uv * 6.0);                   // cloud
  else                   return (uv.x + uv.y) * 0.5;               // diagonal
}

void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);

  float m = clamp(lumaMap(vTexSamplingCoord, uPattern), 0.0, 1.0);
  if (uInvert > 0.5) m = 1.0 - m;

  // Expand the sweep so the wipe fully clears at both ends despite softness.
  float soft = max(uSoftness, 0.001);
  float t = uThreshold * (1.0 + 2.0 * soft) - soft;
  float reveal = 1.0 - smoothstep(t - soft, t + soft, m);

  // Single-input pipeline: reveal the incoming clip from black.
  gl_FragColor = vec4(src.rgb * reveal, 1.0);
}
