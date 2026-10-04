#version 100
// Aspect-ratio canvas with letterbox fill.
//
// Fits the source frame inside the target aspect ratio and fills the leftover
// bars with either a flat colour or a blurred, zoomed-to-cover copy of the
// frame itself - the "blurred background" look every social editor uses for
// portrait video on a landscape canvas (and vice versa).
//
// Doing this in one shader matters: once Media3's Presentation has letterboxed
// the frame, the bars are already solid black and the original pixels outside
// the fit region are gone, so the blur has to be produced in the same pass
// that performs the fit.
precision mediump float;

uniform sampler2D uTexSampler;
uniform float uSrcAspect;   // source width / height
uniform float uDstAspect;   // target width / height
uniform float uMode;        // 0 = flat colour, 1 = blurred cover
uniform vec3  uFillColor;
uniform float uBlurAmount;  // sample spread, in source UV units

varying vec2 vTexSamplingCoord;

// 13-tap rotated-disc blur. Cheap enough for realtime preview and smooth
// enough that no banding shows on a large out-of-focus background.
vec3 blurSample(vec2 uv, float radius) {
  vec3 acc = texture2D(uTexSampler, uv).rgb;
  float count = 1.0;
  for (int i = 0; i < 12; i++) {
    float a = float(i) * 0.5235988;           // 30 degrees apart
    float r = radius * (0.45 + 0.55 * fract(float(i) * 0.618));
    vec2 off = vec2(cos(a), sin(a)) * r;
    acc += texture2D(uTexSampler, clamp(uv + off, 0.0, 1.0)).rgb;
    count += 1.0;
  }
  return acc / count;
}

void main() {
  vec2 uv = vTexSamplingCoord;

  // Map the output canvas back onto the source, preserving aspect (contain).
  vec2 fitUv = uv;
  float scaleX = 1.0;
  float scaleY = 1.0;
  if (uDstAspect > uSrcAspect) {
    // Canvas is wider than the frame: pillarbox, so squeeze horizontally.
    scaleX = uDstAspect / uSrcAspect;
  } else {
    // Canvas is taller: letterbox, so squeeze vertically.
    scaleY = uSrcAspect / uDstAspect;
  }
  fitUv = (uv - 0.5) * vec2(scaleX, scaleY) + 0.5;

  bool inside = fitUv.x >= 0.0 && fitUv.x <= 1.0 && fitUv.y >= 0.0 && fitUv.y <= 1.0;

  if (inside) {
    gl_FragColor = vec4(texture2D(uTexSampler, fitUv).rgb, 1.0);
    return;
  }

  if (uMode < 0.5) {
    gl_FragColor = vec4(uFillColor, 1.0);
    return;
  }

  // Blurred background: map the canvas onto the source as "cover" instead of
  // "contain" so the whole canvas is filled, then blur and darken slightly so
  // the sharp foreground still reads as the subject.
  vec2 coverUv = uv;
  if (uDstAspect > uSrcAspect) {
    coverUv.y = (uv.y - 0.5) * (uSrcAspect / uDstAspect) + 0.5;
  } else {
    coverUv.x = (uv.x - 0.5) * (uDstAspect / uSrcAspect) + 0.5;
  }
  vec3 bg = blurSample(clamp(coverUv, 0.0, 1.0), uBlurAmount);
  gl_FragColor = vec4(bg * 0.82, 1.0);
}
