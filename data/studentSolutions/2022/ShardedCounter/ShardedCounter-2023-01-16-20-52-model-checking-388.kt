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

    private fun help() {

    }


    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        // TODO: use Random.nextInt() % ARRAY_SIZE to choose the cell
        makeOperation(Op.INC)

    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        return makeOperation(Op.GET)
    }

    private fun makeOperation(op: Op): Int {
        var result: Int = 0
        while (true) {
            val index = Random().nextInt(ARRAY_SIZE)

            if (!lock()) {
                continue
            }

            if (op == Op.INC) {
                counters[index].incrementAndGet()
            } else {
                for (i in 0 until ARRAY_SIZE) {
                    result += counters[i].value
                }
            }


            unlock()

            return result
        }
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME