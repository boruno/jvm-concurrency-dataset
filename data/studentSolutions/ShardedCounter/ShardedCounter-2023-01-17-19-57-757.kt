//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.concurrent.ThreadLocalRandom

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc(): Int {
        return counters[ThreadLocalRandom.current().nextInt(ARRAY_SIZE)].incrementAndGet()
        // TODO: use Random.nextInt() % ARRAY_SIZE to choose the cell
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var res = 0

        for (i in 0 until ARRAY_SIZE)
            res += counters[i].value

        return res
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME