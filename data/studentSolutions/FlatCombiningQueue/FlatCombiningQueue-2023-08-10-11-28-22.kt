//package day4

import Result
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
        if (combinerLock.compareAndSet(false, true)) {
            queue.addLast(element)
            performTheOperations()
        } else {
            while (true) {
                val index = randomCellIndex()
                if (!tasksForCombiner.compareAndSet(index, null, element)) {
                    continue
                }
                @Suppress("ControlFlowWithEmptyBody")
                while (!tasksForCombiner.compareAndSet(index, Result, null) && combinerLock.get()) {
                }
                break
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
            performTheOperations()
            return queue.removeFirstOrNull()
        } else {
            while (true) {
                val index = randomCellIndex()
                if (!tasksForCombiner.compareAndSet(index, null, Dequeue)) {
                    continue
                }
                @Suppress("ControlFlowWithEmptyBody")
                while (!tasksForCombiner.compareAndSet(index, Result, null) && combinerLock.get()) {
                }
            }
        }
        return null
    }

    // TODO: On success, apply this operation and help others by traversing
    // TODO: `tasksForCombiner`, performing the announced operations, and
    // TODO: updating the corresponding cells to `Result`.
    private fun performTheOperations() {
        require(combinerLock.get()) { "Lock isn't acquired" }
        for (index in 0 until TASKS_FOR_COMBINER_SIZE) {
            val cell = tasksForCombiner.get(index)
            if (cell == null || cell is Result<*>) {
                continue
            }
            if (cell !is Dequeue) {
                tasksForCombiner.set(index, Result(queue.removeFirstOrNull()))
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