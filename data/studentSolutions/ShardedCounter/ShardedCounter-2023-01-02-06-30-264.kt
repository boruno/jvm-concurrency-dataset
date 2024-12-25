//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    private val lockers = List(ARRAY_SIZE) {Locker()}

    private class Locker {
        val locked = atomic(false)

        fun lock() = locked.compareAndSet(false, update = true)

        fun unlock() = locked.compareAndSet(true, update = false)
    }
    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        // TODO: use Random.nextInt() % ARRAY_SIZE to choose the cell
        while (true) {
            val cell = Random.nextInt(ARRAY_SIZE)
            if (lockers[cell].lock()) {
                counters[cell] += 1
                lockers[cell].unlock()
            }
        }
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var sum = 0
        for (i in 0 until ARRAY_SIZE) {
            while (true) {
                if (lockers[i].lock()) break
            }
            sum += counters[i].value
        }
        for (i in 0 until ARRAY_SIZE) lockers[i].unlock()
        return sum
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME