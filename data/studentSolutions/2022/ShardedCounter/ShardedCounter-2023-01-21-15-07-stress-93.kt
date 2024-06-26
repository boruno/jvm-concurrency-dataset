package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val index = Random.nextInt() % ARRAY_SIZE
        counters[index].getAndIncrement()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var sum = 0
        for (ind in 0 until ARRAY_SIZE) {
            sum += counters[ind].getAndSet(0)
        }
        return sum
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME