package mpp.counter

import kotlin.random.Random
import kotlinx.atomicfu.AtomicIntArray

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
//        TODO("implement me!")
        // TODO: use Random.nextInt() % ARRAY_SIZE to choose the cell
        while (true) {
            val id = Random.nextInt() % ARRAY_SIZE
            val value = counters[id].value
            if (counters[id].compareAndSet(value, value + 1)) {
                return
            }
        }
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
//        TODO("implement me!")
        var ans: Int = 0
        for (i in 0 until counters.size) {
            ans += counters[i].value
        }
        return ans
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME