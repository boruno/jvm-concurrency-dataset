//package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E : Any> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // TODO: 2a. On success, apply this operation and help others by traversing
        // TODO:     `tasksForCombiner`, performing the announced operations, and
        // TODO:      updating the corresponding cells to `Result`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` with the element. Wait until either the cell state
        // TODO:      updates to `Result` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.

        return doOperation(task = element) {
            queue.addLast(element)
        }
    }

    override fun dequeue(): E? {
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // TODO: 2a. On success, apply this operation and help others by traversing
        // TODO:     `tasksForCombiner`, performing the announced operations, and
        // TODO:      updating the corresponding cells to `Result`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` with `Dequeue`. Wait until either the cell state
        // TODO:      updates to `Result` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.

        return doOperation(task = Dequeue) {
            queue.removeFirstOrNull()
        }
    }

    private fun <R> doOperation(task: Any, operation: () -> R): R {
        var taskIndex: Int? = null

        while (true) {
            if (lock()) {
                return doOperationWithLock(taskIndex, operation)
            }
            else {
                if (taskIndex == null) {
                    val index = randomCellIndex()
                    if (tasksForCombiner[index].compareAndSet(null, task)) {
                        taskIndex = index
                    }
                } else {
                    val cellValue = tasksForCombiner[taskIndex].value
                    if (cellValue is Result<*>) {
                        tasksForCombiner[taskIndex].value = null
                        @Suppress("UNCHECKED_CAST")
                        return cellValue.value as R
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R> doOperationWithLock(taskIndex: Int?, operation: () -> R): R {
        try {
            val taskValue = if (taskIndex != null) {
                // Only one thread which acquired lock can perform cell execution.
                // Here exactly this thread acquired the lock, so value can't be changed to Result after reading
                val taskValue = tasksForCombiner[taskIndex].value
                tasksForCombiner[taskIndex].value = null
                taskValue
            } else {
                null
            }

            val result = if (taskValue is Result<*>) {
                taskValue.value as R
            } else {
                operation()
            }

            processTasks()
            return result
        } finally {
            combinerLock.value = false
        }
    }

    private fun processTasks() {
        for (i in 0 until tasksForCombiner.size) {
            val cell = tasksForCombiner[i]
            when (val cellValue = cell.value) {
                null -> continue
                Dequeue -> {
                    val firstElementFromQueue = queue.removeFirstOrNull()
                    cell.value = Result(firstElementFromQueue)
                }

                else -> {
                    @Suppress("UNCHECKED_CAST")
                    val value = cellValue as E
                    queue.addLast(value)
                    cell.value = Result(Unit)
                }
            }
        }
    }

    private fun lock(): Boolean {
        return combinerLock.compareAndSet(false, true)
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

//private class Enqueue<V>(
//    val value: V
//)

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)