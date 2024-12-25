@file:Suppress("UNCHECKED_CAST", "ControlFlowWithEmptyBody")

//package day4

import day1.Queue
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReferenceArray

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
        if (combinerLock.compareAndSet(false, true)) {
            try {
                queue.addLast(element)
                help()
            } finally {
                combinerLock.compareAndSet(true, false)
            }
        } else {
            var index = randomCellIndex()
            while (!tasksForCombiner.compareAndSet(index, null, element)) {
                index = randomCellIndex()
            }
            while (true) {
                val result = tasksForCombiner.get(index)
                if (result is Result<*> && result.value == true) {
                    tasksForCombiner.compareAndSet(index, result, null)
                    return
                }
                if (combinerLock.compareAndSet(false, true)) {
                    try {
                        help()
                    } finally {
                        combinerLock.compareAndSet(true, false)
                    }
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
        if (combinerLock.compareAndSet(false, true)) {
            try {
                val result = queue.removeFirstOrNull()
                help()
                return result
            } finally {
                combinerLock.compareAndSet(false, true)
            }
        } else {
            var index = randomCellIndex()
            while (!tasksForCombiner.compareAndSet(index, null, Dequeue)) {
                index = randomCellIndex()
            }
            while (true) {
                val result = tasksForCombiner.get(index)
                if (result is Result<*>) {
                    tasksForCombiner.compareAndSet(index, result, null)
                    return result.value as E?
                }
                if (combinerLock.compareAndSet(false, true)) {
                    try {
                        help()
                    } finally {
                        combinerLock.compareAndSet(true, false)
                    }
                }
            }
        }
    }

    private fun help() {
        for (i in 0 until tasksForCombiner.length()) {
            when (val task = tasksForCombiner.get(i)) {
                null -> continue
                is Dequeue -> tasksForCombiner.compareAndSet(i, task, Result(queue.removeFirstOrNull()))
                else -> {
                    queue.addLast(task as E)
                    tasksForCombiner.compareAndSet(i, task, Result(true))
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.length())
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

private object Dequeue

private class Result<V>(
    val value: V
)