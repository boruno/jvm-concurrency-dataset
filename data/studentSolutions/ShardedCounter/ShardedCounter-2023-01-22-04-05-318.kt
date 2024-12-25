//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicLong

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private val random = ThreadLocalRandom.current()
    private val version = AtomicLong(0)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        version.incrementAndGet()
        val idx = random.nextInt(ARRAY_SIZE)
        counters[idx].incrementAndGet()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        while (true) {
            val version1 = version.getAcquire()
            var result = 0
            for (i in 0..1) {
                result += counters[i].value
            }
            val version2 = version.get()
            if (version1 == version2) return result
        }
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME