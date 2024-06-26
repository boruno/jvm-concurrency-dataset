package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.*

fun IntRange.random() =
    Random().nextInt((endInclusive + 1) - start) + start

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val index = (0..10).random() % ARRAY_SIZE
        counters[index] += 1
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var sum : Int = 0
        for (i in 0..ARRAY_SIZE) {
            sum += counters[i].value
        }
        return sum
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME