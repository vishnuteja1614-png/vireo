package com.vireo.editor.engine

/**
 * Converts Media3's frame timestamps into a clip-local timeline.
 *
 * Inside an [androidx.media3.transformer.EditedMediaItemSequence] the
 * presentation timestamp handed to an effect does **not** restart at zero for
 * each item - frames are offset so the muxer receives a monotonically
 * increasing timeline. Clip two therefore starts at clip one's duration.
 *
 * Effects that assumed a zero origin (transitions, caption windows) silently
 * did nothing on every clip after the first: their animation progress was
 * already clamped to 1.0 on the very first frame they saw.
 *
 * This records whatever timestamp it is handed first and reports everything
 * afterwards relative to it. That is correct whether the library resets per
 * item or accumulates, so it cannot break if Media3 changes its behaviour.
 *
 * One instance is shared by every effect belonging to the same clip, so the
 * transition and the captions agree on where that clip begins.
 */
class ClipClock {

    @Volatile
    private var originUs: Long = UNSET

    /** Clip-local microseconds, never negative. */
    fun localUs(presentationTimeUs: Long): Long {
        if (originUs == UNSET) {
            // Frames arrive in order on a single render thread, so this is safe.
            originUs = presentationTimeUs
        }
        return (presentationTimeUs - originUs).coerceAtLeast(0L)
    }

    private companion object { const val UNSET = Long.MIN_VALUE }
}
