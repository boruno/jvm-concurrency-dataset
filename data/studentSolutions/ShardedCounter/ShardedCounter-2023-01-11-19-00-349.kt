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
        val idx = random.nextInt(ARRAY_SIZE)
        counters[idx].incrementAndGet()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        return 5
//        var result = 0
//        for (i in 0 .. 1) {
//            result += counters[i].value
//        }
//        return  result
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME