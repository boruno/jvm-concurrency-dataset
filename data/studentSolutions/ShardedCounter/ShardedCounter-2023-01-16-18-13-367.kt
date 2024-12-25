//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.locks.ReentrantLock

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private var lock = ReentrantLock()
    private var shard = 0


    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        if (lock.tryLock()) {
            val index = Thread.currentThread().id.toInt() % ARRAY_SIZE
            try {
                counters[index].incrementAndGet()
            } finally {
                lock.unlock()
            }
        } else {
            while (!lock.tryLock()) {
                // wait
            }
            val index = Thread.currentThread().id.toInt() % ARRAY_SIZE
            try {
                counters[index].incrementAndGet()
            } finally {
                lock.unlock()
            }
        }
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var sum = 0
        for (i in 0 until ARRAY_SIZE) {
            sum += counters[i].value
        }
        return sum
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME