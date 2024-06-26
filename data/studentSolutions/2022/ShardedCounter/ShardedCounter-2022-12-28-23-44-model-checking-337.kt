package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import kotlin.random.Random
import kotlin.random.Random.Default.nextInt

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val ind = nextInt() % ARRAY_SIZE
        counters[ind].getAndIncrement()
        TODO("implement me!")
        // TODO: use Random.nextInt() % ARRAY_SIZE to choose the cell
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var total = 0
        for (i in 0 until counters.size) {
            total += counters[i].value
        }
        return total
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME