# Vireo — Deep Code Audit (round 2)

Triggered by the report: *"filter, text, merge, compress, captions, recent projects —
so many errors… transitions not applying in video."*

Every item below was traced to a specific line, not guessed. Severity:
**P0** = feature is completely broken, **P1** = feature is half-built,
**P2** = works but is below a desktop editor's standard.

---

## P0 — Transitions never render (the reported bug)

**Root cause: the timestamp origin assumption is wrong.**

`TransitionEffects` computed animation progress as:

```kotlin
progress = presentationTimeUs / durationUs
```

This assumes `presentationTimeUs` **restarts at 0 for every clip**. It does not.
Inside an `EditedMediaItemSequence`, Media3 offsets each item's frames so the
muxer receives a monotonically increasing timeline. Clip 2 therefore begins at
whatever clip 1's duration was.

Consequence:

| Clip | First timestamp seen | `progress` on frame 1 | Visible result |
|------|----------------------|------------------------|----------------|
| 1 | 0 µs | 0.0 | transition plays correctly |
| 2 | 8,000,000 µs | 13.3 → clamped to **1.0** | **transition already finished — nothing animates** |
| 3+ | larger still | **1.0** | **nothing animates** |

So the transition only ever worked on the very first clip, which looks like
"transitions don't work" in any real project.

**Fix:** a per-clip `ClipClock` that records the first timestamp it is handed and
reports every later timestamp relative to it. This is origin-agnostic — it is
correct whether Media3 resets per item or accumulates, so it cannot regress if
the library changes.

## P0 — Captions never render (same root cause)

`CaptionOverlay` made the mirror-image mistake: it subtracted `clipStartMs`
from the cue time, assuming per-clip timestamps that reset. With cumulative
timestamps the window `startUs..endUs` is compared against a much larger
number, so `getBitmap()` returned the blank bitmap for the whole clip.

**Fix:** same `ClipClock`, then convert to an absolute timeline position
(`clipStartMs + clipLocal`) and compare against the cue's absolute window.

## P0 — Text is impossible to edit

`EditorTool` declares `TEXT` and `AUDIO`:

```kotlin
enum class EditorTool { NONE, SPLIT, SPEED, FILTER, TEXT, AUDIO, TRANSITION, VOLUME }
```

but `ToolPanel`'s `when` block only handles `SPEED`, `FILTER`, `VOLUME` and
`TRANSITION`. **`TEXT` has no panel at all.** The toolbar does:

```kotlin
RailItem(..., "Text") { vm.addText(); tool = EditorTool.NONE }
```

which inserts a hardcoded `TextOverlay(text = "Your title")` and immediately
closes the panel. `updateText()` exists in the ViewModel but **is never called
from anywhere in the UI**.

Net effect: the user gets the words "Your title" burned into their export with
no way to change the words, position, size, colour or timing.

**Fix:** a real `TEXT` panel — content field, size, colour swatches, X/Y
placement, start/end timing, and delete — wired to `updateText`/`removeText`.

## P0 — Captions are text-only, with no caption source

The caption picker sets `project.captionStyleId`, and the exporter styles
`project.texts` with it. But nothing ever *creates* caption cues. There is no
speech-to-text, no SRT import, and no "split script into timed cues" action.
A user picks a gorgeous Hormozi style and sees nothing, because there are no
cues to style.

**Fix (this round):** generate timed cues from the AI script / any text, split
by word count, distributed across the timeline. Real on-device ASR is a
separate, larger job (noted below).

## P0 — Recent projects are lost the moment the app closes

`MainActivity`:

```kotlin
var recent by remember { mutableStateOf<List<Project>>(emptyList()) }
```

In-memory `remember` only. There is **no Room, no DataStore, no file store** —
a repo-wide grep for persistence returns nothing. Worse, `recent` is only
appended when the user backs out of the editor; exporting and returning Home
does not record anything.

**Fix:** a JSON project store in `filesDir`, saved on every mutation (debounced),
loaded at startup. Deliberately not Room: no schema migrations to maintain,
and the dataset is tiny.

## P1 — "Merge" and "Compress" are decorative

`MainActivity.onQuickTool`:

```kotlin
when (tool) {
    "ai" -> nav.navigate("ai")
    "settings" -> nav.navigate("settings")
    "captions" -> nav.navigate("captions")
    else -> { vm.loadGallery(MediaKind.VIDEO); nav.navigate("picker") }   // trim, merge, compress
}
```

`trim`, `merge` and `compress` all fall into the same `else` and just open the
picker. Nothing sets a merge mode; nothing opens compression settings.

**Fix:** route each quick tool to a real intent — merge pre-selects multi-pick
and goes straight to export; compress jumps to the export screen with a size
target and a live estimated-size readout.

## P1 — Filters are applied but there are only 8, and no manual grade

`FilterFactory` is correct (the RGB matrices are properly column-major, and the
NOIR luminance weights are right). The problem is scope: 8 presets, and
`brightness`/`contrast`/`saturation` exist on the model but have **no UI** —
the Filter panel only shows preset chips. A desktop editor exposes the grade.

## P1 — No preview of effects

Filters, transitions and captions are only visible *after* a multi-minute
export. The preview player shows the raw clip. This is why problems went
unnoticed for so long, and it is the single biggest usability gap.

## P2 — Missing features vs a desktop editor

Not bugs, but gaps against the "PC editor" bar:

**Timeline:** no multi-track video, no magnetic snapping, no ripple delete,
no track locking, no markers, no J/K/L shuttle, no keyframes.
**Audio:** no waveform display, no fade handles on the timeline, no ducking,
no noise reduction, no beat detection.
**Video:** no crop/pan-zoom (Ken Burns), no stabilisation, no chroma key,
no masks, no blend modes, no PiP, no reverse, no freeze-frame, no LUT import.
**Text:** no fonts beyond system families, no text animation presets wired to
`TextAnim`, no stickers, no shapes.
**Project:** no autosave indicator, no versioning, no proxy/optimised media,
no background export queue.

---

## Research note — live web/social data (item 8 on the original crosscheck)

The app still reasons purely from model knowledge. Checked for keyless options:

- **Wikipedia** has a genuinely open REST API
  (`https://en.wikipedia.org/api/rest_v1/page/summary/<title>`) — no key, CORS
  open, generous limits. Usable immediately.
- **Google** has no free search API. The Custom Search JSON API is capped at
  100 queries/day and needs a key; scraping `google.com/search` violates the
  ToS and is blocked by consent interstitials.
- **DuckDuckGo** Instant Answer API (`api.duckduckgo.com/?q=…&format=json`) is
  keyless but returns only abstracts, not ranked results.
- **YouTube Data API** needs a key; 10,000 units/day free.
- **Trends/social counts** have no keyless source worth shipping.

Realistic plan: Wikipedia + DuckDuckGo Instant Answers give a keyless
"research" tier; anything deeper needs the user's own key.

---

## Fix order

1. `ClipClock` → transitions and captions actually render **(P0, this round)**
2. Text editing panel **(P0, this round)**
3. Project persistence + real recent list **(P0, this round)**
4. Caption cue generation from script **(P0, this round)**
5. Merge / compress quick tools **(P1, this round)**
6. Manual colour grade UI + more presets **(P1)**
7. Effect preview in the player **(P1 — biggest usability win)**
8. Desktop-grade timeline: keyframes, crop/pan-zoom, chroma key, waveforms **(P2, staged)**
