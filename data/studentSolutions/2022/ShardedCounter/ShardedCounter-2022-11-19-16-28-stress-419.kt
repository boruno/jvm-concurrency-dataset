package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.*

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val i = Random().nextInt() % ARRAY_SIZE
        counters[i].incrementAndGet()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        return counters[0].value
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME