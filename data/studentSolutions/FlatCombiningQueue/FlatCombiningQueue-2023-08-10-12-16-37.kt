//package day4

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

        performOperation<E> { element as Any }
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

        return performOperation { Dequeue }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <V> performOperation(createTask: () -> Any): V {
        var taskWasInstalledAt = -1

        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                // We are the Combiner.

                // Do our own stuff unless it was installed.
                var theResult = if (taskWasInstalledAt == -1) doOperation(createTask()) else null

                // Help others.
                for (i in 0 until tasksForCombiner.length()) {
                    val task = tasksForCombiner.get(i) ?: continue

                    if (i == taskWasInstalledAt) {
                        // Our task was here, extract it.
                        theResult = when (task) {
                            is Result<*> -> processOurResult(taskWasInstalledAt, task)
                            else -> doOperation(task)
                        }

                    } else if (task !is Result<*>) {
                        // Perform another operation and save the result.
                        tasksForCombiner.set(i, Result(doOperation(task)))
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
                        return processOurResult(taskWasInstalledAt, task)
                    }
                    else -> {}
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <V> processOurResult(taskWasInstalledAt: Int, result: Result<*>): V {
        assert(taskWasInstalledAt >= 0)
        assert(tasksForCombiner.get(taskWasInstalledAt) === result)
        tasksForCombiner.set(taskWasInstalledAt, null)
        return result.value as V
    }

    @Suppress("UNCHECKED_CAST")
    private fun doOperation(task: Any): Any? {
        return when (task) {
            is Result<*> -> throw IllegalStateException()
            Dequeue -> queue.removeFirstOrNull()
            else -> queue.addLast(task as E)
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