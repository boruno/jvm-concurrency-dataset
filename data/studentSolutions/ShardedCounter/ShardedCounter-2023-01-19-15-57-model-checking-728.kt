package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.*

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private val random = Random()

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val index = random.nextInt() % ARRAY_SIZE
        counters[index].incrementAndGet()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var res = 0
        for (i in 0 until ARRAY_SIZE) {
            res += counters[i].value
        }
        return res
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME