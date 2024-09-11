package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.ReentrantLock
import java.util.*

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private val locked = atomic(false);

    enum class Op {
        INC, GET
    }

    private fun lock(): Boolean {
        return locked.compareAndSet(expect = false, update = true)
    }

    private fun unlock() {
        locked.value = false
    }


    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        // TODO: use Random.nextInt() % ARRAY_SIZE to choose the cell
        while (true) {
            val index = Random().nextInt() % ARRAY_SIZE
            if (!lock() || !counters.get(index)
                    .compareAndSet(expect = counters.get(index).value, update = counters.get(index).value + 1)
            ) {
                continue
            }
            unlock()
            return
        }
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        while (true) {
            if (!lock()) {
                continue
            }
            var result = 0
            for (i in 0 until ARRAY_SIZE) {
                result += counters.get(i).value
            }

            unlock()
            return result
        }
    }

}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME
