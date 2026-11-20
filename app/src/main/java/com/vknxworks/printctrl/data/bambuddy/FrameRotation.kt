package com.vknxworks.printctrl.data.bambuddy

/**
 * Hands out a few picture buffers in turn, so a camera stream doesn't need a new one for every frame, and
 * makes sure a buffer is only written again once nothing can be showing it any more.
 *
 * Every frame that is shown gets the next number. A buffer may be reused when the frame shown after the one
 * it holds has "settled": the screens have had time to switch to that newer frame (or one after it), so
 * none of them still draws the buffer. Whoever shows the frames reports that with [settle]. Until then
 * [reusable] returns nothing and the caller makes a new buffer, exactly as if there were no reuse.
 *
 * [reusable], [shown] and [clear] must be called one at a time (the stream's decoder does, under a lock);
 * [settle] may be called from another thread.
 */
class FrameRotation<T : Any>(private val size: Int = 3) {
    private val buffers = arrayOfNulls<Any>(size)
    private val numbers = LongArray(size)
    private var next = 0
    private var last = 0L

    /** Every frame up to this number has been replaced on screen for long enough. */
    @Volatile private var settled = 0L

    /** The buffer whose turn it is, when it's safe to write to it now; otherwise null. */
    @Suppress("UNCHECKED_CAST")
    fun reusable(): T? {
        val buffer = buffers[next] ?: return null
        return if (settled > numbers[next]) buffer as T else null
    }

    /**
     * A new frame is being shown. [buffer] is what holds it if that can be written to again later, or null
     * if it can't be reused. It takes the turn [reusable] was asked about. Returns the frame's number.
     */
    fun shown(buffer: T?): Long {
        last++
        buffers[next] = buffer
        numbers[next] = last
        next = (next + 1) % size
        return last
    }

    /** The screens have had time to switch to frame [number]: every frame before it is no longer drawn. */
    fun settle(number: Long) {
        if (number > settled) settled = number
    }

    /** Lets go of every buffer (the stream stopped); numbering goes on. */
    fun clear() {
        for (i in buffers.indices) buffers[i] = null
    }
}
