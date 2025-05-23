//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import java.util.concurrent.ThreadLocalRandom

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private val random = ThreadLocalRandom.current()
    private val version = atomic(0L)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val idx = random.nextInt(ARRAY_SIZE)
        version.incrementAndGet()
        counters[idx].incrementAndGet()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        while (true) {
            val version = this.version.value
            var result = 0
            for (i in 0..1) {
                result += counters[i].value
            }
            if (this.version.value == version) {
                return result
            }
        }
    }
}

private const val ARRAY_SIZE = 10 // DO NOT CHANGE ME