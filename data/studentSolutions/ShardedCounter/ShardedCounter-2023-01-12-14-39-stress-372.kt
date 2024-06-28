package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val idx = Random.nextInt() % ARRAY_SIZE
        counters[idx].incrementAndGet()
        // TODO: use Random.nextInt() % ARRAY_SIZE to choose the cell
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        return counters[0].value + counters[1].value
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME