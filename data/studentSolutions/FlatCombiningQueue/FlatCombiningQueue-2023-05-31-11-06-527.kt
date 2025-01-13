//package day2

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // TODO: 2a. On success, apply this operation and help others by traversing `tasksForCombiner`, performing the announced operations, and updating the corresponding cells to `PROCESSED`.
        // TODO: 2b. If the lock is already acquired, announce this operation in `tasksForCombiner` by replacing a random cell state from  `null` to the element. Wait until either the cell state  updates to `PROCESSED` (do not forget to clean it in this case),  or `combinerLock` becomes available to acquire.
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                // We are the combiner
                try {
                    queue.addLast(element)
                    executeTasks()
                } finally {
                    combinerLock.value = false
                }
                return
            } else {
                // We are not the combiner
                var idx = randomCellIndex()
                while (!tasksForCombiner[idx].compareAndSet(expect = null, update = element)) {
                    idx = randomCellIndex()
                }
                // Wait for our task to be executed or we become a combiner
                while (true) {
                    if (tasksForCombiner[idx].compareAndSet(expect = PROCESSED, update = null)) {
                        return
                    } else if (combinerLock.compareAndSet(expect = false, update = true)) {
                        tasksForCombiner[idx].value = null
                        executeTasks()
                        return
                    }
                }
            }
        }
    }

    override fun dequeue(): E? {
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // TODO: 2a. On success, apply this operation and help others by traversing `tasksForCombiner`, performing the announced operations, and updating the corresponding cells to `PROCESSED`.
        // TODO: 2b. If the lock is already acquired, announce this operation in `tasksForCombiner` by replacing a random cell state from  `null` to `DEQUE_TASK`. Wait until either the cell state  updates to `PROCESSED` (do not forget to clean it in this case), or `combinerLock` becomes available to acquire.
        var result: E? = null
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                // We are the combiner
                try {
                    result = queue.removeFirstOrNull()
                    executeTasks()
                } finally {
                    combinerLock.value = false
                }
                return result
            } else {
                // We are not the combiner
                var idx = randomCellIndex()
                while (!tasksForCombiner[idx].compareAndSet(expect = null, update = DEQUE_TASK)) {
                    idx = randomCellIndex()
                }
                // Wait for our task to be executed or we become a combiner
                while (true) {
                    if (tasksForCombiner[idx].compareAndSet(expect = PROCESSED, update = null)) {
                        return result
                    } else if (combinerLock.compareAndSet(expect = false, update = true)) {
                        tasksForCombiner[idx].value = null
                        executeTasks()
                        return result
                    }
                }
            }
        }
    }

    private fun executeTasks() {
        for (idx in 0 until tasksForCombiner.size) {
            val task = tasksForCombiner[idx].getAndSet(PROCESSED)
            if (task == DEQUE_TASK) {
                queue.removeFirstOrNull()
            } else if (task != null && task != PROCESSED) {
                queue.addLast(task as E)
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue(). enqueue()-s should put the inserting element.
private val DEQUE_TASK = Any()

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED = Any()