//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.concurrent.ThreadLocalRandom

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val idx = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
        counters.get(idx) += 1
        // TODO("implement me!")
        // TODO: use Random.nextInt() % ARRAY_SIZE to choose the cell
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var res = 0
        for (i in 0..ARRAY_SIZE) {
            res += counters.get(i).value
        }
        return res
        // TODO("implement me!")
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME