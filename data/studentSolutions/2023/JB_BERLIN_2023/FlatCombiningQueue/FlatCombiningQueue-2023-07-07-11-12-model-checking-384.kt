package day4

import day1.*
import kotlinx.atomicfu.*
import java.lang.IllegalStateException
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
                queue.addLast(element)
                helpOthers()
                return
            }
            else {
                val i = randomCellIndex()
                if (tasksForCombiner[i].compareAndSet(null, element)) {
                    waitForEnqueueResultOrLock(i, element)
                    return
                }
                else { // the cell is occupied
                    continue
                }
            }
        }
    }

    private fun waitForEnqueueResultOrLock(i: Int, elementToEnqueue: E) {
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                when (val task = tasksForCombiner[i].getAndSet(null)) {
                    is Result<*> -> {
                        // the task was executed by other thread, do nothing
                    }
                    elementToEnqueue -> {
                        queue.addLast(elementToEnqueue)
                    }
                    else -> {
                        throw IllegalStateException("Can't happen, element: $task")
                    }
                }
                helpOthers()
                return
            }
            else {
                val item = tasksForCombiner[i].value
                if (item is Result<*>) {
                    tasksForCombiner[i].value = null // TODO ? do we need CAS here?
                    return
                }
            }
        }
    }

    private fun helpOthers() {
        for (i in 0..TASKS_FOR_COMBINER_SIZE - 1) {
            when(val item = tasksForCombiner[i].value) {
                is Dequeue -> {
                    val element = queue.removeFirstOrNull()
                    tasksForCombiner[i].value = Result(element)
                }
                is Result<*> -> {
                    // already done => ignore
                    continue
                }
                null -> {
                    // no tasks => move on
                    continue
                }
                else -> {
                    // element to enqueue
                    queue.addLast(item as E)
                    tasksForCombiner[i].compareAndSet(item, Result(item))
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
                val value = queue.removeFirstOrNull()
                helpOthers()
                return value
            }
            else {
                val i = randomCellIndex()
                if (tasksForCombiner[i].compareAndSet(null, Dequeue)) {
                    val result = waitForDequeueResultOrLock(i)
                    return result
                }
                else { // the cell is occupied
                    continue
                }
            }
        }

    }

    private fun waitForDequeueResultOrLock(i: Int) : E? {
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                val value = when (val task = tasksForCombiner[i].getAndSet(null)) {
                    Dequeue -> {
                        // the task was not executed yet => do it ourselves
                        queue.removeFirstOrNull()
                    }
                    is Result<*> -> {
                        // the task was executed by other thread
                        task.value as E
                    }
                    else -> {
                        throw IllegalStateException("Can't happen, element: $task")
                    }
                }
                helpOthers()
                return value
            }
            else {
                val task = tasksForCombiner[i].value
                if (task is Result<*>) {
                    tasksForCombiner[i].value = null // TODO ? do we need CAS here?
                    return task.value as E
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