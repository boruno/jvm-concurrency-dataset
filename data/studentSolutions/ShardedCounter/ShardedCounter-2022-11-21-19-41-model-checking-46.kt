package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.concurrent.ThreadLocalRandom

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val where = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
        counters[where].getAndIncrement()
    }

    fun incByDelta(delta: Int) {
        val where = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
        counters[where].getAndAdd(delta)
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var count = 0
        for (i in 0 until ARRAY_SIZE) {
            count += counters[i].value
        }
        return count
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME