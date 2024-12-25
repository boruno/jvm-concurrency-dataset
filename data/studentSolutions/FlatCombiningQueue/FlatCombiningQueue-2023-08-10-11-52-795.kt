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

        while (true) {
            // (1)
            if (tryLock()) {
                // (2a)
                enqueueWithLock(element)
                return
            }
            // (2b)
            val randomCellIndex = randomCellIndex()
            if (tasksForCombiner.compareAndSet(randomCellIndex, null, element)) {
                waitUntilResultOrEnqueueWithLock(randomCellIndex, element)
                return
            }
        }
    }

    private fun waitUntilResultOrEnqueueWithLock(randomCellIndex: Int, element: E) {
        while (true) {
            tasksForCombiner.get(randomCellIndex)?.also { result ->
                if (result is Result<*>) {
                    if (tasksForCombiner.compareAndSet(randomCellIndex, result, null)) {
                        return
                    }
                }
            }
            if (tryLock()) {
                enqueueWithLock(element)
                return
            }
        }
    }

    private fun enqueueWithLock(element: E) {
        queue.addLast(element).also {
            helpToDoTasks()
            unlock()
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
        while (true) {
            if (tryLock()) {
                // (2a)
                return dequeueWithLock()
            }
            // (2b)
            val randomCellIndex = randomCellIndex()
            if (tasksForCombiner.compareAndSet(randomCellIndex, null, Dequeue)) {
                return waitForResultOrDequeueWithLock(randomCellIndex)
            }
        }
    }

    private fun waitForResultOrDequeueWithLock(randomCellIndex: Int): E? {
        while (true) {
            tasksForCombiner.get(randomCellIndex)?.also { result ->
                if (result is Result<*>) {
                    if (tasksForCombiner.compareAndSet(randomCellIndex, result, null)) {
                        return result.value as E?
                    }
                }
            }
            if (tryLock()) {
                return dequeueWithLock()
            }
        }
    }

    private fun tryLock() = combinerLock.compareAndSet(false, true)
    private fun unlock() = combinerLock.set(false)

    private fun dequeueWithLock(): E? {
        return queue.removeFirstOrNull().also {
            helpToDoTasks()
            unlock()
        }
    }

    private fun helpToDoTasks() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            tasksForCombiner.get(i)?.also { element ->
                when (element) {
                    is Dequeue -> {
                        tasksForCombiner.compareAndSet(i, Dequeue, Result(queue.removeFirstOrNull()))
                    }
                    !is Result<*> -> {
                        if (tasksForCombiner.compareAndSet(i, element, Result(element))) {
                            queue.addLast(element as E)
                        }
                    }
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
    val value: V,
)