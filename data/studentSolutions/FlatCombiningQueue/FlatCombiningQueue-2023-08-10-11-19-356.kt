//package day4

import Result
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        val cell = randomCellIndex()
        var hasCell = false
        while (true) {
            if (hasCell && tasksForCombiner.get(cell) == null) {
                return
            }
            if (combinerLock.compareAndSet(false, true)) {
                try {
                    if (hasCell && tasksForCombiner.get(cell) == null) {
                        return
                    }
                    queue.addLast(element)
                    help()
                    return
                }
                finally {
                    combinerLock.set(false)
                }
            }
            if (!hasCell) {
                hasCell = tasksForCombiner.compareAndSet(cell, null, element)
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

    private fun help() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner.get(i) ?: continue
            when (task) {
                is Result<*> -> {
                    continue
                }

                is Dequeue -> {
                    tasksForCombiner.set(i, Result(queue.removeFirstOrNull()))
                }

                else -> {
                    queue.addLast(task as E)
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
        // TODO:      updating the corresponding cells to `Result`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` with `Dequeue`. Wait until either the cell state
        // TODO:      updates to `Result` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        val cell = randomCellIndex()
        var hasCell = false
        while (true) {
            if (hasCell && tasksForCombiner.get(cell) is Result<*>) {
                return (tasksForCombiner.getAndSet(cell, null) as Result<*>).value as E
            }
            if (combinerLock.compareAndSet(false, true)) {
                try {
                    if (hasCell && tasksForCombiner.get(cell) is Result<*>) {
                        return (tasksForCombiner.getAndSet(cell, null) as Result<*>).value as E
                    }
                    val result = queue.removeFirstOrNull()
                    help()
                    return result
                }
                finally {
                    combinerLock.set(false)
                }
            }
            if (!hasCell) {
                hasCell = tasksForCombiner.compareAndSet(cell, null, Dequeue)
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