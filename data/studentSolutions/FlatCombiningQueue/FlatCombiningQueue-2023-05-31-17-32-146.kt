package day2

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)
    private val processedDeque = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun help() {
        val randCell = randomCellIndex()
        val task = tasksForCombiner[randCell]
        val taskVal = task.value ?: return
        if (taskVal == PROCESSED) return
        if (task.compareAndSet(taskVal, PROCESSED)) {
            queue.addLast(taskVal as E)
        }

        if (task.compareAndSet(DEQUE_TASK, PROCESSED)) {
            processedDeque[randCell].getAndSet(queue.removeFirstOrNull())
        }
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
        if (combinerLock.compareAndSet(false, true)) {
            queue.addLast(element)
            help()
            combinerLock.compareAndSet(true, false)
        } else {
            val randCell = randomCellIndex()
            val task = tasksForCombiner[randCell]
            if (task.compareAndSet(null, element)) {
                var currIter = 0
                while (currIter < 1) {
                    currIter += 1
                }
                while (true) {
                    if (task.compareAndSet(PROCESSED, null)) {
                        return
                    }
                    if (task.compareAndSet(element, null)) {
                        break
                    }
                }
            }
            enqueue(element)
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
            help()
            combinerLock.compareAndSet(true, false)
            return result
        } else {
            val randCell = randomCellIndex()
            val task = tasksForCombiner[randCell]
            if (task.compareAndSet(null, DEQUE_TASK)) {
                var currIter = 0
                while (currIter < 1) {
                    currIter += 1
                }
                if (task.compareAndSet(PROCESSED, null)) {
                    return processedDeque[randCell].getAndSet(null) as? E
                }
            }
            return dequeue()
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