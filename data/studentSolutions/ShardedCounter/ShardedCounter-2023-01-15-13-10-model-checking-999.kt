package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    private val locked = atomic(false)

    /**
     * Atomically increments by one the current value of the counter.
     */
    @Suppress("ControlFlowWithEmptyBody", "BooleanLiteralArgument")
    fun inc() {
        while (locked.compareAndSet(true, true));

        counters[Random.nextInt() % ARRAY_SIZE].incrementAndGet()
    }

    /**
     * Returns the current counter value.
     */
    @Suppress("BooleanLiteralArgument")
    fun get(): Int {
        locked.compareAndSet(false, true)
        val result = (0 until ARRAY_SIZE).fold(0) { t, c -> t + counters[c].value}
        locked.compareAndSet(true, false)
        return result
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME
