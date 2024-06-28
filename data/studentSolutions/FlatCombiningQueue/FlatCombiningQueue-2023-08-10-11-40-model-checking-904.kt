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
        if (combinerLock.compareAndSet(false, true)) {
            if (!tasksForCombiner.compareAndSet(randomCellIndex(), Dequeue, Result(element))) {
                queue.addLast(element)
            }
        } else {
            val cellIndex = randomCellIndex()
            if (tasksForCombiner.compareAndSet(cellIndex, null, element)) {
                while (true) {
                    if (tasksForCombiner.compareAndSet(cellIndex, Result(null), null)) {
                        return
                    }
                    if (combinerLock.compareAndSet(false, true)) {
                        queue.addLast(element)
                        return
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
        val cellIndex = randomCellIndex()
        return if (combinerLock.compareAndSet(false, true)) {
            val randomCell = tasksForCombiner.get(randomCellIndex())
            if (randomCell is Result<*> && randomCell != Result(null) ) {
                tasksForCombiner.compareAndSet(cellIndex, randomCell, null)
                combinerLock.compareAndSet(true, false)
                return randomCell.value as E
            } else {
                val removedElement = queue.removeFirstOrNull()
                combinerLock.compareAndSet(true, false)
                return removedElement
            }
        } else {
            waitDeque(cellIndex)
        }
    }

    private fun waitDeque(cellIndex: Int): E? {
        if (tasksForCombiner.compareAndSet(cellIndex, null, Dequeue)) {
            while (true) {
                val result = tasksForCombiner.get(cellIndex)
                if (result is Result<*> && result.value != null) {
                    return result.value as E
                }
                if (combinerLock.compareAndSet(false, true)) {
                    return queue.removeFirstOrNull()
                }
            }
        }
        return null
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