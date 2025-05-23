//package day2

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
        var index = randomCellIndex()
        var pushed = false
        while (!combinerLock.compareAndSet(false, true)) {
            if (pushed) {
                if (tasksForCombiner[index].compareAndSet(PROCESSED, null)) return
            } else {
                if (tasksForCombiner[index].compareAndSet(null, element)) pushed = true
                else index = randomCellIndex()
            }
        }
        for (taskIndex in 0..(tasksForCombiner.size - 1)) {
            val localElement = tasksForCombiner[taskIndex].value ?: continue
            if (localElement == PROCESSED) continue
            if (localElement == DEQUE_TASK) {
                tasksForCombiner[taskIndex].compareAndSet(DEQUE_TASK, queue.removeFirstOrNull())
                continue
            }
            if (tasksForCombiner[taskIndex].compareAndSet(localElement, PROCESSED)) {
                queue.addLast(localElement as E)
            }
        }
        if (!pushed) queue.addLast(element)
        combinerLock.compareAndSet(true, false)
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
        var index = randomCellIndex()
        var pushed = false
        while (!combinerLock.compareAndSet(false, true)) {
            if (pushed) {
                val element = tasksForCombiner[index].value
                if (element != DEQUE_TASK) {
                    tasksForCombiner[index].compareAndSet(element, null)
                    return element as E?
                }
            } else {
                if (tasksForCombiner[index].compareAndSet(null, DEQUE_TASK)) pushed = true
                else index = randomCellIndex()
            }
        }
        for (taskIndex in 0..(tasksForCombiner.size - 1)) {
            val localElement = tasksForCombiner[taskIndex].value ?: continue
            if (localElement == PROCESSED) continue
            if (localElement == DEQUE_TASK) {
                tasksForCombiner[taskIndex].compareAndSet(DEQUE_TASK, queue.removeFirstOrNull())
                continue
            }
            if (tasksForCombiner[taskIndex].compareAndSet(localElement, PROCESSED)) {
                queue.addLast(localElement as E)
            }
        }
        val element: E?
        if (!pushed) {
            element = queue.removeFirstOrNull()
        } else {
            element = tasksForCombiner[index].value as E?
            tasksForCombiner[index].compareAndSet(element, null)
        }
        combinerLock.compareAndSet(true, false)
        return element
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