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
        val index = Random.nextInt() % ARRAY_SIZE
        println(index)
        counters[index].incrementAndGet()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        return (0 until ARRAY_SIZE).sumOf { counters[it].value }
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME