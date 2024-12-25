//package day2

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun combinerHelp() {
        for (taskId in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[taskId].value
            if (task != null) {
                when (task) {
                    DEQUE_TASK -> {
                        val result = dequeue()
                        tasksForCombiner[taskId].compareAndSet(task, Processed(result))
                    }

                    else -> {
                        enqueue(task as E)
                        tasksForCombiner[taskId].compareAndSet(task, Processed())
                    }
                }
            }
        }
    }
    override fun enqueue(element: E) {
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                // combiner now
                queue.add(element)
                combinerHelp()
                combinerLock.compareAndSet(true, false)
                return
            } else {
                // try to put tasks
                val taskId = randomCellIndex()
                if (tasksForCombiner[taskId].compareAndSet(null, element)) {
                    // put the task sucessfully
                    while (tasksForCombiner[taskId].value !is Processed && combinerLock.value == true) {
                        // do nothing
                    }

                    val result = tasksForCombiner[taskId].value
                    if (result is Processed) {
                        tasksForCombiner[taskId].compareAndSet(result, null)
                        return
                    }
                }
            }
        }
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // TODO: 2a. On success, apply this operation and help others by traversing
        // TODO:     `tasksForCombiner`, performing the announced operations, and
        // TODO:      updating the corresponding cells to `PROCESSED`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` to the element. Wait until either the cell state
        // TODO:      updates to `PROCESSED` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
    }

    override fun dequeue(): E? {
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                // combiner now
                val result = queue.removeFirstOrNull()
                combinerHelp()
                combinerLock.compareAndSet(true, false)
                return result
            } else {
                // try to put tasks
                val taskId = randomCellIndex()
                if (tasksForCombiner[taskId].compareAndSet(null, DEQUE_TASK)) {
                    // put the task sucessfully
                    while (tasksForCombiner[taskId].value !is Processed && combinerLock.value == true) {
                        // do nothing
                    }

                    val result = tasksForCombiner[taskId].value
                    if (result is Processed) {
                        tasksForCombiner[taskId].compareAndSet(result, null)
                        return result.result as E?
                    }
                }
            }
        }
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // TODO: 2a. On success, apply this operation and help others by traversing
        // TODO:     `tasksForCombiner`, performing the announced operations, and
        // TODO:      updating the corresponding cells to `PROCESSED`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` to `DEQUE_TASK`. Wait until either the cell state
        // TODO:      updates to `PROCESSED` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private val DEQUE_TASK = Any()

// TODO: Put this token in `tasksForCombiner` when the task is processed.
class Processed(val result: Any? = null)