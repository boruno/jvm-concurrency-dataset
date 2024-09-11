package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

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
            for (i in 0 until tasksForCombiner.size) {
                val task = tasksForCombiner[i].value
                if (task == null || task == Result)
                    continue

                when (task) {
                    is Dequeue -> {
                        tasksForCombiner[i].compareAndSet(task, queue.removeFirstOrNull())
                    }

                    else -> {
                        tasksForCombiner[i].compareAndSet(task, queue.addLast(task as E))
                    }
                }
            }
            combinerLock.value = false
        } else {
            while (true) {
                val randomCellIndex = randomCellIndex()
                val task = tasksForCombiner[randomCellIndex].value
                if (task == null) {
                    if (tasksForCombiner[randomCellIndex].compareAndSet(null, element)) {
                        while (true) {
                            val result = tasksForCombiner[randomCellIndex].value
                            if (result is Result<*>) {
                                return
                            }
                        }
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
            val result = queue.removeFirstOrNull()
            for (i in 0 until tasksForCombiner.size) {
                val task = tasksForCombiner[i].value
                if (task == null || task == Result)
                    continue

                when (task) {
                    is Dequeue -> {
                        tasksForCombiner[i].compareAndSet(task, queue.removeFirstOrNull())
                    }

                    else -> {
                        tasksForCombiner[i].compareAndSet(task, queue.addLast(task as E))
                    }
                }
            }
            combinerLock.value = false
            return result
        } else {
            while (true) {
                val randomCellIndex = randomCellIndex()
                val task = tasksForCombiner[randomCellIndex].value
                if (task == null) {
                    if (tasksForCombiner[randomCellIndex].compareAndSet(null, Dequeue)) {
                        while (true) {
                            val result = tasksForCombiner[randomCellIndex].value
                            if (result is Result<*>) {
                                return result.value as E
                            }
                        }
                    }
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)