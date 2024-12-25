//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        // TODO: use Random.nextInt() % ARRAY_SIZE to choose the cell
        val nextId = Random.nextInt() % ARRAY_SIZE
        counters[nextId].incrementAndGet()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var sum = 0
        repeat(ARRAY_SIZE) {
            sum += counters[it].value
        }
        return sum
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME