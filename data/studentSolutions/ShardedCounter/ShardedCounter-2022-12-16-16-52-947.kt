//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.concurrent.ThreadLocalRandom

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private val random = ThreadLocalRandom.current()

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val i = random.nextInt(0,  ARRAY_SIZE)
        counters[i].incrementAndGet()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var i = 0
        var sum = 0
        repeat(ARRAY_SIZE) {
            sum += counters[i].value
        }
        return sum
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME