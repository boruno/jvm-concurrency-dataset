package day2

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)
    private val processedDeque = atomicArrayOfNulls<E?>(TASKS_FOR_COMBINER_SIZE)

    private fun helpOthers() {
        val randCell = randomCellIndex()
        val task = tasksForCombiner[randCell]
        val taskValue = task.value ?: return
        if (taskValue == PROCESSED) return
        // enqueue
        if (task.compareAndSet(taskValue, PROCESSED)) {
            (taskValue as? E).let {
                if (it != null) {
                    queue.addLast(it)
                }
            }
        }
        // deque
        if (task.compareAndSet(DEQUE_TASK, PROCESSED)) {
            val el = queue.removeFirstOrNull()
            while (!processedDeque[randCell].compareAndSet(null, el)) {}
        }
    }

    private fun tryLock(): Boolean {
        return combinerLock.compareAndSet(false, true)
    }

    private fun unlock() {
        combinerLock.compareAndSet(true, false)
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
            if (tryLock()) {
                queue.addLast(element)
                helpOthers()
                unlock()
                return
            } else {
                val randCell = randomCellIndex()
                val task = tasksForCombiner[randCell]
                if (task.compareAndSet(null, element)) {
                    while (true) {
                        if (task.compareAndSet(PROCESSED, null)) {
                            return
                        }
                    }
                } else {
                    continue
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
            if (tryLock()) {
                val result = queue.removeFirstOrNull()
                helpOthers()
                unlock()
                return result
            } else {
                val randCell = randomCellIndex()
                val task = tasksForCombiner[randCell]
                if (task.compareAndSet(null, DEQUE_TASK)) {
                    while (true) {
                        if (task.compareAndSet(PROCESSED, null)) {
                            val result = processedDeque[randCell].value ?: return null
                            while (!processedDeque[randCell].compareAndSet(result, null)) {}
                            return result
                        }
                    }
                } else {
                    continue
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