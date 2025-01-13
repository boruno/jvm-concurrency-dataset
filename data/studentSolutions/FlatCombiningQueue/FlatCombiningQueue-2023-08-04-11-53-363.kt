//package day4

import Result
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
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                helping()
                queue.addLast(element)
                if (combinerLock.compareAndSet(true, false)) {
                    break
                }
            } else {
                val randomCell = randomCellIndex()
                if (tasksForCombiner[randomCell].compareAndSet(null, Result(element))) {
                    break
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
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                helping()
                val result = queue.removeFirstOrNull()
                if (combinerLock.compareAndSet(true, false)) {
                    return result
                }
            } else {
                val randomCell = randomCellIndex()
                val task = tasksForCombiner[randomCell]
                task.compareAndSet(null, Dequeue)
                val taskVal = task.value
                if (taskVal is Result<*>) {
                    if (task.compareAndSet(taskVal, null)) {
                        return taskVal as E
                    }
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private fun helping() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[i].value
            if (task is kotlin.Result<*>) {
                if (tasksForCombiner[i].compareAndSet(task, null)) {
                    queue.addLast(task as E)
                }
            }
        }
    }
}


private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)