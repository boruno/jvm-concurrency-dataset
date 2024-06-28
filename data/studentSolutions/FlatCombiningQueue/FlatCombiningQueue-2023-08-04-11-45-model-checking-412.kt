package day4

import day1.*
import day4.Result
import kotlinx.atomicfu.*
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun acquireLock(): Boolean = combinerLock.compareAndSet(expect = false, update = true)

    private fun releaseLock() {
        combinerLock.compareAndSet(expect = true, update = false)
    }

    private fun combinerWork() {
        for (i in 0 until tasksForCombiner.size) {
            tasksForCombiner[i].getAndUpdate { task ->
                when (task) {
                    is Result<*> -> Unit// do nothing
                    null -> Unit // skip
                    Dequeue -> Result(queue.removeFirstOrNull() as E)
                    else -> Result(null).also {
                        queue.addLast(task as E)
                    }
                }
            }
        }
    }

    private fun queueOperation(task: Any?): Int {
        while (true) {
            val randomIndex = randomCellIndex()
            if (tasksForCombiner[randomIndex].compareAndSet(null, task)) {
                return randomIndex
            }
        }
    }

    override fun enqueue(element: E) {
        var queuedOperationIndex: Int? = null
        while (true) {
            if (acquireLock()) {
                try {
                    queue.addLast(element)

                    // cleanup task if being set
                    queuedOperationIndex?.let {
                        tasksForCombiner[it].compareAndSet(element, null)
                    }

                    // help others
                    combinerWork()
                } finally {
                    releaseLock()
                }

                return
            }

            if (queuedOperationIndex != null) {
                val result = tasksForCombiner[queuedOperationIndex].value
                if (result is Result<*>) {
                    // my operation is completed by other threads
                    return
                }
            } else {
                queuedOperationIndex = queueOperation(element)
            }
        }
    }

    override fun dequeue(): E? {
        var queuedOperationIndex: Int? = null
        while (true) {
            if (acquireLock()) {
                return try {
                    val element = queue.removeFirstOrNull()

                    // cleanup task if being set
                    queuedOperationIndex?.let {
                        tasksForCombiner[it].compareAndSet(Dequeue, null)
                    }

                    // help others
                    combinerWork()

                    element
                } finally {
                    releaseLock()
                }
            }

            if (queuedOperationIndex != null) {
                val result = tasksForCombiner[queuedOperationIndex].value
                if (result is Result<*>) {
                    // my operation is completed by other threads
                    return result.value as E
                }
            } else {
                queuedOperationIndex = queueOperation(Dequeue)
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