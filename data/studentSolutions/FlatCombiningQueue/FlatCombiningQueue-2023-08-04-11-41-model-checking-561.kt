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

            handleTasks()

        } else {
            val task = Enqueue(element)
            var index: Int = -1

            while (true) {
                index = randomCellIndex()
                if (tasksForCombiner[index].compareAndSet(null, task)) {
                    break
                }
            }

            while (true) {
                val value = tasksForCombiner[index].value

                if (value is Result<*>) {
                    if (!tasksForCombiner[index].compareAndSet(value, null)) {
                        continue
                    }
                }

                if (combinerLock.compareAndSet(false, true)) {
                    if (tasksForCombiner[index].compareAndSet(task, null)) {
                        queue.addLast(element)
                    }

                    handleTasks()
                }
            }
        }
        //queue.addLast(element)
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
            val removed = queue.removeFirstOrNull()

            handleTasks()

            return removed
        } else {
            val task = Dequeue
            var index: Int = -1

            while (true) {
                index = randomCellIndex()

                if (tasksForCombiner[index].compareAndSet(null, task)) {
                    break
                }
            }

            while (true) {
                val value = tasksForCombiner[index].value

                if (value is Result<*>) {
                    if (!tasksForCombiner[index].compareAndSet(value, null)) {
                        continue
                    } else {
                        return value.value as E?
                    }
                }

                if (combinerLock.compareAndSet(false, true)) {
                    var removed: E? = null

                    if (tasksForCombiner[index].compareAndSet(task, null)) {
                        removed = queue.removeFirstOrNull()
                    }

                    handleTasks()

                    return removed
                }
            }
        }
        //return queue.removeFirstOrNull()
    }

    private fun handleTasks() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[i].value

            if (task is Dequeue) {
                val removed = queue.removeFirstOrNull()
                tasksForCombiner[i].value = removed
            }
            if (task is Enqueue<*>) {
                val add = queue.addLast(task.value as E)
                tasksForCombiner[i].value = add
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

private data class Enqueue<V>(val value: V)


// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)