//package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun tryLock(): Boolean {
        return combinerLock.compareAndSet(false, true)
    }

    private fun unlock() {
        combinerLock.compareAndSet(true, false)
    }

    fun applyOps() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val op = tasksForCombiner[i].value
            when(op) {
                Dequeue -> tasksForCombiner[i].compareAndSet(op, Result(doDequeue()))
                is Result<*> -> {}
                null -> {}
                else -> tasksForCombiner[i].compareAndSet(op, Result(doEnqueue(op as E)))
            }
        }
    }

    fun doEnqueue(element: E) {
        return queue.addLast(element)
    }

    fun doDequeue(): E? {
        return queue.removeFirstOrNull()
    }

//    fun <T, R> uniHelp(op: (T) -> R, arg: T): R {
//        val ind = randomCellIndex()
//        while (true) {
//            if (tryLock()) {
//                val r = op(arg)
//                applyOps()
//                return r
//            }
//
//            tasksForCombiner[ind].compareAndSet(null, element)
//
//            val v = tasksForCombiner[ind].value
//            if (v is Result<*>) {
//                tasksForCombiner[ind].compareAndSet(v, null)
//                return
//            }
//        }
//    }

    override fun enqueue(element: E) {
        var ind: Int = -1
        var taskAdded = false
        while (true) {
            if (tryLock()) {
                try {
                    applyOps()
                    if (taskAdded) continue
                    doEnqueue(element)
                    return
                } finally {
                    unlock()
                }
            }

            if (!taskAdded) ind = randomCellIndex()
            taskAdded = taskAdded || tasksForCombiner[ind].compareAndSet(null, element)
            if (!taskAdded) continue

            val v = tasksForCombiner[ind].value
            if (v is Result<*>) {
                tasksForCombiner[ind].compareAndSet(v, null)
                return
            }
        }
    }

    override fun dequeue(): E? {
        var ind = -1
        var taskAdded = false
        while (true) {
            if (tryLock()) {
                try {
                    applyOps()
                    if (taskAdded) continue
                    return doDequeue()
                } finally {
                    unlock()
                }
            }

            if (!taskAdded) ind = randomCellIndex()
            taskAdded = taskAdded || tasksForCombiner[ind].compareAndSet(null, Dequeue)
            if (!taskAdded) continue

            val v = tasksForCombiner[ind].value
            if (v is Result<*>) {
                tasksForCombiner[ind].compareAndSet(v, null)
                return v.value as E
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)