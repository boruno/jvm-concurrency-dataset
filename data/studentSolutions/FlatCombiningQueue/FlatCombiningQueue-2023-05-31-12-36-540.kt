//package day2

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun combine() {
        for (it in 0 until tasksForCombiner.size) {
            when (val task = tasksForCombiner[it].value) {
                null, PROCESSED -> {}
                DEQUE_TASK -> {
                    tasksForCombiner[it].value = queue.removeFirstOrNull()
//                    tasksForCombiner[it].compareAndSet(DEQUE_TASK, PROCESSED)
                }
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    queue.addLast(task as E)
                    tasksForCombiner[it].value = PROCESSED
//                    tasksForCombiner[it].compareAndSet(task, PROCESSED)
                }
            }
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

        if (combinerLock.compareAndSet(expect = false, update = true)) {
            queue.addLast(element)
            combine()
            combinerLock.value = false
        } else {
            val index = acquireRandomCellWith(element)

            while (true) {
                if (tasksForCombiner[index].compareAndSet(PROCESSED, null)) {
                    return
                }

                if (combinerLock.compareAndSet(expect = false, update = true)) {
                    combine()
                    tasksForCombiner[index].value = null
                    combinerLock.value = false
                    break
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

        if (combinerLock.compareAndSet(expect = false, update = true)) {
            return queue.removeFirstOrNull().also {
                combine()
                combinerLock.value = false
            }
        } else {
            val index = acquireRandomCellWith(DEQUE_TASK)

            while (true) {
                val task = tasksForCombiner[index].value

                if (task != DEQUE_TASK && task != PROCESSED && task != null) {
                    @Suppress("UNCHECKED_CAST")
                    return task as E
                }

                if (combinerLock.compareAndSet(expect = false, update = true)) {
                    combine()
                    @Suppress("UNCHECKED_CAST")
                    return (tasksForCombiner[index].value as E).also {
                        tasksForCombiner[index].value = null
                        combinerLock.value = false
                    }
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private fun acquireRandomCellWith(element: Any?): Int {
        while (true) {
            val index = randomCellIndex()

            if (tasksForCombiner[index].compareAndSet(null, element)) {
                return index
            }
        }
    }
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private val DEQUE_TASK = Any()

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED = Any()