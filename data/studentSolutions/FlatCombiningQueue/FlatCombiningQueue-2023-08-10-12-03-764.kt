//package day4

import java.util.concurrent.*
import java.util.concurrent.atomic.*

@Suppress("UNCHECKED_CAST")
class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun performCombinerTasks() {
        for (idx in 0 until tasksForCombiner.length()) {
            when (val current = tasksForCombiner.get(idx)) {
                Empty -> {}
                Dequeue -> {
                    val dequed = queue.removeFirstOrNull()
                    tasksForCombiner.set(idx, Result(dequed))
                }

                is Result<*> -> {}

                // new element
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    queue.addLast(current as E)
                    tasksForCombiner.set(idx, Result(Unit))
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
        // TODO:      updating the corresponding cells to `Result`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` with the element. Wait until either the cell state
        // TODO:      updates to `Result` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        while (true) {
            if (lockCombinerLock()) {
                queue.addLast(element)

                performCombinerTasks()
                unlockCombinerLock()

                return
            } else {
                val randomIdx = randomCellIndex()
                // if the index is bad, we will just try again
                if (!tasksForCombiner.compareAndSet(randomIdx, Empty, element)) continue

                waitForResult(randomIdx)
                return
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
        while (true) {
            if (lockCombinerLock()) {
                val element = queue.removeFirstOrNull()

                performCombinerTasks()
                unlockCombinerLock()

                return element
            } else {
                val randomIdx = randomCellIndex()
                // if the index is bad, we will just try again
                if (!tasksForCombiner.compareAndSet(randomIdx, Empty, Dequeue)) continue

                val result = waitForResult(randomIdx)
                return result.value as E
            }
        }
    }

    private fun lockCombinerLock(): Boolean = combinerLock.compareAndSet(false, true)

    private fun unlockCombinerLock() {
        require(combinerLock.compareAndSet(true, false))
    }

    private fun waitForResult(randomIdx: Int): Result<*> {
        while (tasksForCombiner.get(randomIdx) !is Result<*>) {
            if (lockCombinerLock()) {
                performCombinerTasks()
                unlockCombinerLock()
            }
        }

        return tasksForCombiner.getAndSet(randomIdx, Empty) as Result<*>
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

private object Empty