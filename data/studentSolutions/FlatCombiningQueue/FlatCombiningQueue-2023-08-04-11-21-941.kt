//package day4

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        var taskWasInstalled = false
        var cellIdx = randomCellIndex()

        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                if (taskWasInstalled) {
                    tasksForCombiner[cellIdx].value = null
                }
                queue.addLast(element)

                // Help others.
                for (i in 0 until tasksForCombiner.size) {
                    when (val task = tasksForCombiner[i].value) {
                        null -> continue
                        is Dequeue -> tasksForCombiner[i].value = Result(queue.removeFirstOrNull())
                        else -> tasksForCombiner[i].value = Result(queue.addLast(task as E))
                    }
                }

                // Unlock.
                combinerLock.value = false
                return
            }

            // Failed to acquire the lock.
            if (taskWasInstalled) {
                if (tasksForCombiner[cellIdx].value is Result<*>) {
                    tasksForCombiner[cellIdx].value = null
                    return
                }
            } else {
                if (tasksForCombiner[cellIdx].compareAndSet(null, element)) {
                    taskWasInstalled = true
                }
                else {
                    cellIdx = randomCellIndex()
                }
            }
        }

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
    }

    override fun dequeue(): E? {
        var taskWasInstalled = false
        var cellIdx = randomCellIndex()

        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                if (taskWasInstalled) {
                    tasksForCombiner[cellIdx].value = null
                }
                val element = queue.removeFirstOrNull()

                // Help others.
                for (i in 0 until tasksForCombiner.size) {
                    when (val task = tasksForCombiner[i].value) {
                        null -> continue
                        is Dequeue -> tasksForCombiner[i].value = Result(queue.removeFirstOrNull())
                        else -> tasksForCombiner[i].value = Result(queue.addLast(task as E))
                    }
                }

                // Unlock.
                combinerLock.value = false
                return element
            }

            // Failed to acquire the lock.
            if (taskWasInstalled) {
                when (val res = tasksForCombiner[cellIdx].value) {
                    is Result<*> -> {
                        tasksForCombiner[cellIdx].value = null
                        return res.value as E?
                    }
                }
            } else {
                if (tasksForCombiner[cellIdx].compareAndSet(null, Dequeue)) {
                    taskWasInstalled = true
                }
                else {
                    cellIdx = randomCellIndex()
                }
            }
        }

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