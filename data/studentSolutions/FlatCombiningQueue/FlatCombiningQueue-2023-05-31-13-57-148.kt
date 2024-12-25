//package day2

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
        // TODO:      updating the corresponding cells to `PROCESSED`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` to the element. Wait until either the cell state
        // TODO:      updates to `PROCESSED` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        if (combinerLock.compareAndSet(false, true)) {
            queue.addLast(element)
            performCombinerTasks()
            combinerLock.value = false
        }
        else {
            while (true) {
                val ind = randomCellIndex()
                if (tasksForCombiner[ind].compareAndSet(null, element)) {
                    while (true) {
                        if (tasksForCombiner[ind].compareAndSet(PROCESSED, null)) {
                            return
                        }
                        if (combinerLock.compareAndSet(false, true)) {
                            if (!tasksForCombiner[ind].compareAndSet(PROCESSED, null)) {
                                queue.addLast(element)
                                tasksForCombiner[ind].value = null
                            }
                            performCombinerTasks()
                            combinerLock.value = false
                            return
                        }
                    }
                }
            }
        }
    }

    private fun performCombinerTasks() {
        for (ind in 0 until tasksForCombiner.size) {
            val task = tasksForCombiner[ind].value
            if (task === DEQUE_TASK) {
                tasksForCombiner[ind].value = queue.removeFirstOrNull()
            }
            else if (task != null && task !== PROCESSED) {
                queue.addLast(task as E)
                tasksForCombiner[ind].value = PROCESSED
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
        if (combinerLock.compareAndSet(false, true)) {
            val result = queue.removeFirstOrNull()
            performCombinerTasks()
            combinerLock.value = false
            return result
        }
        else {
            while (true) {
                val ind = randomCellIndex()
                if (tasksForCombiner[ind].compareAndSet(null, DEQUE_TASK)) {
                    while (true) {
                        val result = tasksForCombiner[ind].value
                        if (result !== DEQUE_TASK && result !== PROCESSED) {
                            tasksForCombiner[ind].value = null
                            return result as E?
                        }
                        if (combinerLock.compareAndSet(false, true)) {
                            var res = tasksForCombiner[ind].value
                            if (res === DEQUE_TASK) {
                                res = queue.removeFirstOrNull()
                            }
                            tasksForCombiner[ind].value = null
                            performCombinerTasks()
                            combinerLock.value = false
                            return res as E?
                        }
                    }
                }
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