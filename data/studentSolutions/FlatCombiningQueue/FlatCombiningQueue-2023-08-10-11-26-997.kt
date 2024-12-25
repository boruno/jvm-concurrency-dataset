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
            enqueueAsCombiner(element)
            return
        }

        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                enqueueAsCombiner(element)
                return
            }

            val publishIndex = tryPublish(element)
            if (publishIndex != null) {
                waitPublishedEnqueue(publishIndex, element)
                return
            }
        }
    }


    fun enqueueAsCombiner(element: E) {
        queue.addLast(element)
        help()
        combinerLock.set(false)
    }


    fun waitPublishedEnqueue(index: Int, element: E) {
        while (true) {
            var cellState = tasksForCombiner.get(index)
            if (cellState is Result<*>) {
                tasksForCombiner.set(index, null)
                return
            }

            if (combinerLock.compareAndSet(false, true)) {
                cellState = tasksForCombiner.get(index)
                tasksForCombiner.set(index, null)

                if (cellState is Result<*>) {
                    return
                } else {
                    enqueueAsCombiner(element)
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
            return dequeueAsCombiner()
        }

        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                return dequeueAsCombiner()
            }

            val publishIndex = tryPublish(Dequeue)
            if (publishIndex != null) {
                return waitPublishedDequeue(publishIndex)
            }
        }
    }

    fun dequeueAsCombiner(): E? {
        val element = queue.removeFirstOrNull()
        help()
        combinerLock.set(false)
        return element
    }

    fun waitPublishedDequeue(index: Int): E? {
        while (true) {
            var cellState = tasksForCombiner.get(index)
            if (cellState is Result<*>) {
                tasksForCombiner.set(index, null)
                return cellState.value as E?
            }

            if (combinerLock.compareAndSet(false, true)) {
                cellState = tasksForCombiner.get(index)
                tasksForCombiner.set(index, null)
                if (cellState is Result<*>) {
                    return cellState.value as E?
                } else {
                    return dequeueAsCombiner()
                }
            }
        }
    }


    fun tryPublish(element: Any?): Int? {
        val index = randomCellIndex()
        return if (tasksForCombiner.compareAndSet(index, null, element)) {
            index
        } else {
            null
        }
    }


    fun help() {
        for (idx in 0 until tasksForCombiner.length()) {
            val cellState = tasksForCombiner[idx]
            when (cellState) {
                is Dequeue -> {
                    tasksForCombiner.set(idx, Result(queue.removeFirstOrNull()))
                }

                null -> continue
                else -> {
                    queue.addLast(cellState as E)
                    tasksForCombiner.set(idx, Result(cellState))
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