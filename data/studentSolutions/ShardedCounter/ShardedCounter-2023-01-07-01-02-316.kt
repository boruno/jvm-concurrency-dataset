//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import java.util.concurrent.ThreadLocalRandom

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private val locked = atomic(false)

    private fun tryLock() = locked.compareAndSet(expect = false, update = true)
    private fun unlock() {
        locked.value = false
    }

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        while (!tryLock()) continue

        val idx = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
        counters[idx].incrementAndGet()

        unlock()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        while (!tryLock()) continue

        var result = 0
        for (el in 0 until ARRAY_SIZE) {
            result += counters[el].value
        }

        unlock()

        return result
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME