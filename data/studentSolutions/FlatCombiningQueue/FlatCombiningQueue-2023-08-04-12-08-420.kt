//package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun acquireLock(): Boolean = combinerLock.compareAndSet(expect = false, update = true)

    private fun releaseLock() {
        combinerLock.compareAndSet(expect = true, update = false)
    }

    private fun combinerWork() {
        check(combinerLock.value)
        for (i in 0 until tasksForCombiner.size) {
            when (val combinerTask = tasksForCombiner[i].value) {
                null, is Result<*> -> Unit // do nothing

                Dequeue -> tasksForCombiner[i].compareAndSet(Dequeue, Result(queue.removeFirstOrNull() as E))

                else -> {
                    queue.addLast(combinerTask as E)
                    tasksForCombiner[i].compareAndSet(combinerTask, Result(null))
                }
            }
        }
    }

    private fun tryQueueOperation(task: Any?): Int? =
        randomCellIndex().let { randomIndex ->
            if (tasksForCombiner[randomIndex].compareAndSet(null, task)) randomIndex else null
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

            if (queuedOperationIndex == null) {
                queuedOperationIndex = tryQueueOperation(element)
                continue
            }

            val result = tasksForCombiner[queuedOperationIndex].value
            if (result is Result<*>) {
                // my operation is completed by other threads
                tasksForCombiner[queuedOperationIndex].compareAndSet(result, null)
                return
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

            if (queuedOperationIndex == null) {
                queuedOperationIndex = tryQueueOperation(Dequeue)
                continue
            }

            // check if the result is ready
            val result = tasksForCombiner[queuedOperationIndex].value
            if (result is Result<*>) {
                // my operation is completed by other threads
                tasksForCombiner[queuedOperationIndex].compareAndSet(result, null)
                return result.value as E
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