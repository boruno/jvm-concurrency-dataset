//package day2

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    init {
        println()
    }

    override fun enqueue(element: E) {
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
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                queue.addLast(element)
                combinerTasksHelperRoutine()
                combinerLock.compareAndSet(true, false)
                return
            } else {
                val randId = randomCellIndex()
                val task = tasksForCombiner[randId]
                if (task.compareAndSet(null, element)) {
                    repeat(TASKS_FOR_COMBINER_SIZE) {
                        if (task.compareAndSet(PROCESSED, null))
                            return
                    }
                    if (!task.compareAndSet(element, null)) {
                        task.compareAndSet(PROCESSED, null)
                        return
                    }
                }
            }
        }
    }

    override fun dequeue(): E? {
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
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                val elem = queue.removeFirstOrNull()
                combinerTasksHelperRoutine()
                combinerLock.compareAndSet(true, false)
                return elem
            } else {
                val randId = randomCellIndex()
                val task = tasksForCombiner[randId]
                if (task.compareAndSet(null, DEQUE_TASK)) {
                    repeat(TASKS_FOR_COMBINER_SIZE) {
                        val elem = task.value
                        if (elem != DEQUE_TASK) {
                            task.compareAndSet(elem, null)
                            println("elem: $elem is processed? ${elem == PROCESSED} is deque_task? ${elem == DEQUE_TASK}")
                            return elem as E
                        }
                    }
                    if (!task.compareAndSet(DEQUE_TASK, null)) {
                        val elem = task.value
                        task.compareAndSet(elem, null)
                        return elem as E
                    }
                }
            }
        }
    }

    private fun combinerTasksHelperRoutine() {
        repeat(TASKS_FOR_COMBINER_SIZE) { i ->
            val task = tasksForCombiner[i]
            val taskValue = task.value
            if (taskValue != DEQUE_TASK && taskValue != null && taskValue != PROCESSED) {
                queue.addLast(taskValue as E)
                task.compareAndSet(taskValue, PROCESSED)
            } else if (taskValue == DEQUE_TASK) {
                val elem = queue.removeFirstOrNull()
                task.compareAndSet(taskValue, elem)
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private val DEQUE_TASK = Any()

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED = Any()