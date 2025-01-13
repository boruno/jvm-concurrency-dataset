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

        if (tryLock()) {
            doCombiner(element)
        } else {
            while (true) {
                val i = randomCellIndex()
                if (tasksForCombiner.compareAndSet(i, null, element)) {
                    val cell = tasksForCombiner[i]
                    while (true) {
                        if (cell is Result<*>) {
                            tasksForCombiner.compareAndSet(i, cell, null)
                            return
                        }
                        if (tryLock()) {
                            tasksForCombiner.compareAndSet(i, element, null)
                            doCombiner(element)
                        }
                    }
                } else {
                    continue
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
        if (tryLock()) {
            return doCombiner(null)
        } else {
            while (true) {
                val i = randomCellIndex()
                if (tasksForCombiner.compareAndSet(i, null, Dequeue)) {
                    val cell = tasksForCombiner[i]
                    if (cell is Result<*>) {
                        tasksForCombiner.compareAndSet(i, cell, null)
                        return cell.value as E?
                    }
                    if (tryLock()) {
                        tasksForCombiner.compareAndSet(i, Dequeue, null)
                        return doCombiner(null)
                    }
                }
            }
        }
    }

    private fun doCombiner(element: E?): E? {
        try {
            if (element != null) {
                queue.addLast(element)
                help()
                return null
            } else {
                help()
                return queue.removeFirstOrNull()
            }
        } finally {
            unlock()
        }
    }

    private fun help() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val cell = tasksForCombiner[i] ?: continue
            if (cell == Dequeue) {
                val result = Result(queue.removeFirstOrNull())
                tasksForCombiner.compareAndSet(i, Dequeue, result)
                continue
            }
            queue.addLast(cell as E)
            tasksForCombiner[i] = Result(cell)
        }
    }

    private fun unlock() = combinerLock.set(false)

    private fun tryLock() = combinerLock.compareAndSet(false, true)

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