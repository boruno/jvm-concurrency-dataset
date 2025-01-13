//package day2

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E: Any> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

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
        val idx = putTask(element)

        while (true) {
            if (tasksForCombiner[idx].compareAndSet(PROCESSED, null)) {
                return
            }
            @Suppress("BooleanLiteralArgument")
            if (combinerLock.compareAndSet(false, true)) {
                try {
                    combine()
                } finally {
                    combinerLock.value = false
                }
            }
        }

//        @Suppress("BooleanLiteralArgument")
//        if (combinerLock.compareAndSet(false, true)) {
//            try {
//                combine()
//            } finally {
//                combinerLock.value = false
//            }
//        } else {
//            while (true) {
//                if (tasksForCombiner[idx].compareAndSet(PROCESSED, null)) {
//                    return
//                }
//                if (combinerLock.compareAndSet(false, true)) {
//
//                }
//                if (tasksForCombiner[idx])
//            }
//        }

//        queue.addLast(element)
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
        val idx = putTask(DEQUE_TASK)

        while (true) {
            val value = tasksForCombiner[idx].value
            if (value is DequeTaskProcessed<*>) {
                (tasksForCombiner[idx].getAndSet(null) as? DequeTaskProcessed<*>)
                    ?.element
                    ?.let { return it as E? }
            }
            @Suppress("BooleanLiteralArgument")
            if (combinerLock.compareAndSet(false, true)) {
                try {
                    combine()
                } finally {
                    combinerLock.value = false
                }
            }
        }

//        return queue.removeFirstOrNull()
    }

    private fun putTask(task: Any): Int {
        while (true) {
            val idx = randomCellIndex()
            if (tasksForCombiner[idx].compareAndSet(null, task)) {
                return idx
            }
        }
    }

    private fun combine() {
        for (taskIdx in 0 until tasksForCombiner.size) {
            val value = tasksForCombiner[taskIdx].value
            when (value) {
                null -> continue
                DEQUE_TASK -> tasksForCombiner[taskIdx].value = DequeTaskProcessed(queue.removeFirstOrNull())
                else -> {
                    queue.addLast(value as E)
                    tasksForCombiner[taskIdx].value = PROCESSED
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private class DequeTaskProcessed<E>(val element: E?)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private val DEQUE_TASK = Any()

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED = Any()