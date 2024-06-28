package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
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

        var taskIndex = -1
        while (true) {

            if (combinerLock.compareAndSet(expect = false, update = true)) {
                try {
                    val task = if (taskIndex != -1) tasksForCombiner[taskIndex].value else null
                    if (task !is Result<*>) {
                        queue.addLast(element)
                    }
                    tasksForCombiner[taskIndex].compareAndSet(task, null)

                    doCombine()
                } finally {
                    combinerLock.compareAndSet(expect = true, update = false)
                }
                return
            }
            else if (taskIndex == -1){
                taskIndex = addTaskToCombiner(element)
            }
        }
    }

    private fun doCombine() {
        // traverse array
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val cellValue = tasksForCombiner[i].value
            when (cellValue) {
                null, is Result<*> -> continue
                is Dequeue -> {
                    val result = Result(queue.firstOrNull())
                    tasksForCombiner[i].compareAndSet(cellValue, result)
                }
                else -> {
                    val result = Result(cellValue as E)
                    tasksForCombiner[i].compareAndSet(cellValue, result)
                    queue.addLast(cellValue as E)
                }
            }
        }
    }

    private fun addTaskToCombiner(task: Any?): Int {
        val taskIndex = randomCellIndex()
        tasksForCombiner[taskIndex].compareAndSet(null, task)
        return taskIndex
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

        var taskIndex = -1
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                return try {
                    val task = if (taskIndex != -1) tasksForCombiner[taskIndex].value else null
                    val value = if (task !is Result<*>) {
                        queue.removeFirstOrNull()
                    }
                    else {
                        task.value as E?
                    }
                    tasksForCombiner[taskIndex].compareAndSet(task, null)
                    doCombine()
                    value
                } finally {
                    combinerLock.compareAndSet(expect = true, update = false)
                }
            }
            else if (taskIndex == -1) {
                taskIndex = addTaskToCombiner(Dequeue)
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