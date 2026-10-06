package com.vireo.editor.data

import java.util.UUID

/**
 * Frame-accurate three-way trimming.
 *
 * These are deliberately **pure functions** over an immutable clip list: no
 * Android types, no ViewModel, no player. That keeps editing non-destructive
 * (the source file is never touched, only in/out points move) and -- unlike
 * the GPU work -- it makes the maths genuinely provable on the JVM.
 *
 * Timeline model: clips are laid end to end on one track, so a clip's position
 * is the sum of the output durations before it. Deleting time therefore
 * ripples downstream automatically; there are no absolute start stamps to
 * rewrite.
 *
 * Speed matters. A clip played at 2x occupies half as much timeline as source,
 * so converting a timeline offset into a source offset is always
 * `offsetTimeline * speed`, and the reverse is `/ speed`.
 */
object TrimOps {

    /** Nothing shorter than this is worth keeping -- it would be a flicker. */
    const val MIN_CLIP_MS = 200L

    /**
     * Where a timeline position lands.
     *
     * @param index        clip index in the list
     * @param offsetInClip milliseconds into that clip, in timeline time
     */
    data class Hit(val index: Int, val offsetInClip: Long)

    /** Outcome of an edit. [changed] is false when the edit was rejected. */
    data class Result(
        val clips: List<Clip>,
        val changed: Boolean,
        val message: String? = null,
        val selectId: String? = null
    )

    /** Locate the clip under a timeline position. Null when the list is empty. */
    fun hitTest(clips: List<Clip>, positionMs: Long): Hit? {
        var acc = 0L
        clips.forEachIndexed { i, c ->
            val end = acc + c.outputDurationMs
            if (positionMs in acc until end) return Hit(i, positionMs - acc)
            acc = end
        }
        // Landing on or past the final frame still resolves to the last clip.
        if (clips.isNotEmpty() && positionMs >= acc) {
            val last = clips.lastIndex
            return Hit(last, clips[last].outputDurationMs)
        }
        return null
    }

    /** Timeline position at which clip [index] begins. */
    fun startOf(clips: List<Clip>, index: Int): Long =
        clips.take(index).sumOf { it.outputDurationMs }

    /** Convert a timeline offset inside a clip to an absolute source timestamp. */
    private fun sourceCut(clip: Clip, offsetInClip: Long): Long =
        clip.trimStartMs + (offsetInClip * clip.speed).toLong()

    /**
     * Trim Left (cut head): discard from the clip's start up to the playhead.
     * The in-point moves forward; the out-point is untouched.
     */
    fun trimLeft(clips: List<Clip>, playheadMs: Long): Result {
        val hit = hitTest(clips, playheadMs)
            ?: return Result(clips, false, "Move the playhead onto a clip first")
        val clip = clips[hit.index]
        val cut = sourceCut(clip, hit.offsetInClip)

        if (cut <= clip.trimStartMs) return Result(clips, false, "Nothing to trim on the left")
        if (clip.trimEndMs - cut < MIN_CLIP_MS)
            return Result(clips, false, "That would leave the clip too short")

        val out = clips.toMutableList()
        out[hit.index] = clip.copy(trimStartMs = cut)
        return Result(out, true, "Head trimmed", clip.id)
    }

    /**
     * Trim Right (cut tail): discard from the playhead to the clip's end.
     * The out-point moves back; the in-point is untouched.
     */
    fun trimRight(clips: List<Clip>, playheadMs: Long): Result {
        val hit = hitTest(clips, playheadMs)
            ?: return Result(clips, false, "Move the playhead onto a clip first")
        val clip = clips[hit.index]
        val cut = sourceCut(clip, hit.offsetInClip)

        if (cut >= clip.trimEndMs) return Result(clips, false, "Nothing to trim on the right")
        if (cut - clip.trimStartMs < MIN_CLIP_MS)
            return Result(clips, false, "That would leave the clip too short")

        val out = clips.toMutableList()
        out[hit.index] = clip.copy(trimEndMs = cut)
        return Result(out, true, "Tail trimmed", clip.id)
    }

    /**
     * Trim Middle (ripple cut): remove the timeline range [markInMs, markOutMs)
     * and close the gap.
     *
     * The range may span several clips. Clips fully inside it are deleted;
     * clips straddling an edge are trimmed; a range opening and closing inside
     * one clip splits it in two. Because clips are stored sequentially,
     * removing them *is* the ripple -- downstream material slides left by
     * exactly the deleted duration with no further bookkeeping.
     */
    fun trimMiddle(clips: List<Clip>, markInMs: Long, markOutMs: Long): Result {
        val lo = minOf(markInMs, markOutMs)
        val hi = maxOf(markInMs, markOutMs)
        if (hi - lo < MIN_CLIP_MS)
            return Result(clips, false, "Mark a longer range to cut")
        if (clips.isEmpty()) return Result(clips, false, "Timeline is empty")

        val out = mutableListOf<Clip>()
        var acc = 0L
        var removedAny = false
        var selectId: String? = null

        for (clip in clips) {
            val clipStart = acc
            val clipEnd = acc + clip.outputDurationMs
            acc = clipEnd

            // Entirely outside the cut: keep untouched.
            if (clipEnd <= lo || clipStart >= hi) {
                out += clip
                continue
            }
            // Entirely inside the cut: drop it.
            if (clipStart >= lo && clipEnd <= hi) {
                removedAny = true
                continue
            }

            removedAny = true
            val keepHead = clipStart < lo
            val keepTail = clipEnd > hi

            if (keepHead) {
                val cut = sourceCut(clip, lo - clipStart)
                if (cut - clip.trimStartMs >= MIN_CLIP_MS) {
                    val head = clip.copy(trimEndMs = cut)
                    out += head
                    if (selectId == null) selectId = head.id
                }
            }
            if (keepTail) {
                val cut = sourceCut(clip, hi - clipStart)
                if (clip.trimEndMs - cut >= MIN_CLIP_MS) {
                    // A split produces a genuinely new clip; reusing the id
                    // would corrupt selection and the project history.
                    val tail = clip.copy(
                        id = UUID.randomUUID().toString(),
                        trimStartMs = cut
                    )
                    out += tail
                    if (selectId == null) selectId = tail.id
                }
            }
        }

        if (!removedAny) return Result(clips, false, "Marked range covers no clips")
        if (out.isEmpty()) return Result(clips, false, "That would delete the whole timeline")

        return Result(out, true, "Cut ${(hi - lo) / 1000.0}s and closed the gap", selectId)
    }
}
