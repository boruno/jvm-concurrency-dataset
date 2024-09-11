package mpp.counter

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
                shard = 0
                for (i in 0 until ARRAY_SIZE) {
                    shard += counters[i].value
                }
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
                shard = 0
                for (i in 0 until ARRAY_SIZE) {
                    shard += counters[i].value
                }
            } finally {
                lock.unlock()
            }
        }
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        return shard
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME