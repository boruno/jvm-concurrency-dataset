package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        while (true) {
            val index = Random.nextInt() % ARRAY_SIZE
            val currentValue = counters[index].value

            if (counters[index].compareAndSet(currentValue, currentValue + 1)) {
                break
            }
        }
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var sum = 0

        for (counterIndex in 0 until ARRAY_SIZE) {
            val counterValue = counters[counterIndex].value
            sum += counterValue
        }

        return sum
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME