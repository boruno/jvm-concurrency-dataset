package day4

import day1.*
import day4.Result
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
        var published = false
        var idx = -1
        while (true) {
            if (tryLock()) {
                try {
                    if (published) {
                        tasksForCombiner.set(idx, null)
                    }
                    queue.addLast(element)
                    helpOthers()
                    return
                } finally {
                    unlock()
                }
            }

            if (!published) {
                idx = randomCellIndex()
                if (tasksForCombiner.compareAndSet(idx, null, element)) {
                    published = true
                }
            } else {
                if (tasksForCombiner.get(idx) == null) {
                    return
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
        var published = false
        var idx = -1
        while (true) {
            if (tryLock()) {
                try {
                    if (published) {
                        tasksForCombiner.set(idx, null)
                    }
                    val value = queue.firstOrNull()
                    helpOthers()
                    return value
                } finally {
                    unlock()
                }
            }

            if (!published) {
                idx = randomCellIndex()
                if (tasksForCombiner.compareAndSet(idx, null, Dequeue)) {
                    published = true
                }
            } else {
                val result = tasksForCombiner.get(idx)
                if (result is Result<*>) {
                    tasksForCombiner.set(idx, null)
                    return result.value as E
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.length())

    private fun tryLock(): Boolean {
        return combinerLock.compareAndSet(false, true)
    }

    private fun unlock() {
        combinerLock.set(false)
    }

    private fun helpOthers() {
        for (i in 0 until tasksForCombiner.length()) {
            when (val task = tasksForCombiner.get(i)) {
                null -> {
                    continue
                }

                is Dequeue -> {
                    val value = queue.removeFirstOrNull()
                    tasksForCombiner.set(i, Result(value))
                }
                else -> {
                    queue.addLast(task as E)
                    tasksForCombiner.set(i, null)
                }
            }
        }
    }

}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)