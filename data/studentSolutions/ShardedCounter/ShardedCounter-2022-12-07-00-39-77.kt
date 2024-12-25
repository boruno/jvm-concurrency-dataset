//package mpp.counter

import kotlinx.atomicfu.AtomicBooleanArray
import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import java.util.concurrent.ThreadLocalRandom

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private val lock = atomic<Boolean>(false)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        while(lock.value);
        val index = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
        counters[index].getAndIncrement()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        lock.getAndSet(true)
        var sum = 0
        for(i in 0 until ARRAY_SIZE) {
            sum += counters[i].value
        }
        lock.getAndSet(false)
        return sum
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME