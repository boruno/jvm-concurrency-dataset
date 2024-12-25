//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.*

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
//        if (Random().nextBoolean()) {
//            counters[0].incrementAndGet()
//        } else {
            counters[Random().nextInt() % 2].incrementAndGet()
//        }
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        return counters[0].value + counters[1].value
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME