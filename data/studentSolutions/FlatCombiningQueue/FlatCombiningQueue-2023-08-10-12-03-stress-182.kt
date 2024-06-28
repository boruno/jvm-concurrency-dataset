package day4

import day1.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

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

        performOperation(true, { queue.addLast(element) }) { element as Any }
        // TODO:                        what about nullable E? ^
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

        return performOperation(false, { queue.removeFirstOrNull() }) { Dequeue }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <V> performOperation(isEnqueue: Boolean, doOperation: () -> V, createTask: () -> Any): V {
        var taskWasInstalledAt = -1

        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                // We are the Combiner.

                // Do our own stuff unless it was installed.
                var theResult = if (taskWasInstalledAt == -1) doOperation() else null

                // Help others.
                for (i in 0 until tasksForCombiner.length()) {
                    val task = tasksForCombiner.get(i) ?: continue

                    if (i == taskWasInstalledAt) {
                        theResult = when (task) {
                            is Result<*> -> task.value as V
                            else -> doOperation()
                        }

                    } else if (task !is Result<*>) {
                        @Suppress("IMPLICIT_CAST_TO_ANY")
                        val res = when (task) {
                            Dequeue -> queue.removeFirstOrNull()
                            else -> queue.addLast(task as E)
                        }
                        tasksForCombiner.set(i, Result(res))
                    }
                }

                combinerLock.set(false)
                return theResult as V

            } else if (taskWasInstalledAt == -1) {
                // No luck with the lock, we need help from the Combiner.
                val taskIdx = randomCellIndex()
                if (tasksForCombiner.compareAndSet(taskIdx, null, createTask())) {
                    assert(taskIdx >= 0)
                    taskWasInstalledAt = taskIdx
                }

            } else {
                when (val task = tasksForCombiner.get(taskWasInstalledAt)) {
                    is Result<*> -> {
                        tasksForCombiner.set(taskWasInstalledAt, null)
                        return task.value as V
                    }
                    else -> {}
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.length())
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)