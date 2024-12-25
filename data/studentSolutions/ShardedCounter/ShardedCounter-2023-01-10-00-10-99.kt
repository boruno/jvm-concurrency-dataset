//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val rand = ThreadLocalRandom.current().nextInt() % ARRAY_SIZE
        assert(rand in 0 until ARRAY_SIZE)
        counters[rand].incrementAndGet()
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