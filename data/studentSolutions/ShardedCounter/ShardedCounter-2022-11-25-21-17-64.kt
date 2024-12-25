//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import java.util.Random

class ShardedCounter {
    private val commonCounter = atomic(0)
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val index = Random().nextInt(ARRAY_SIZE)
        counters[index].getAndIncrement()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        while (true) {
            var counter = 0
            for (index in 0 until ARRAY_SIZE) {
                while (true) {
                    val oldVal = counters[index].value
                    if (counters[index].compareAndSet(oldVal, 0)) {
                        counter += oldVal
                        break
                    }
                }
            }
            val oldVal = commonCounter.value
            if (commonCounter.compareAndSet(oldVal, oldVal + counter))
                return oldVal + counter
        }
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME