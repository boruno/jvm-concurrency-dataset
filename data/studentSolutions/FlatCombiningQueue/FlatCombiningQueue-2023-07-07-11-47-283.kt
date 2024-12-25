//package day4

import day1.*
import kotlinx.atomicfu.*
import java.lang.IllegalStateException
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        // 1.  Try to become a combiner by
        //     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // 2a. On success, apply this operation and help others by traversing
        //     `tasksForCombiner`, performing the announced operations, and
        //      updating the corresponding cells to `Result`.
        // 2b. If the lock is already acquired, announce this operation in
        //     `tasksForCombiner` by replacing a random cell state from
        //      `null` with the element. Wait until either the cell state
        //      updates to `Result` (do not forget to clean it in this case),
        //      or `combinerLock` becomes available to acquire.

        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                enqueueSequentially(element)
                helpOthersAndReleaseLock()
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

    private fun enqueueSequentially(element: E) {
        queue.addLast(element)
    }

    private fun waitForEnqueueResultOrLock(i: Int, elementToEnqueue: E) {
        val e = waitForResultOrLock(i, true)
        if (e != elementToEnqueue) {
            throw IllegalStateException("Unexpectedly not equal elements, expected: $elementToEnqueue, actual: $e")
        }
    }

    private fun waitForDequeueResultOrLock(i: Int) : E? {
        return waitForResultOrLock(i, false)
    }

    private fun waitForResultOrLock(i: Int, enqueue: Boolean): E? {
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                val value = when (val task = tasksForCombiner[i].getAndSet(null)) {
                    Dequeue -> {
                        if (enqueue) {
                            throw IllegalStateException("Dequeue can't be here, we are enqueing!")
                        }
                        // the task was not executed yet => do it ourselves
                        dequeueSequentially()
                    }
                    is Result<*> -> {
                        // the task was executed by other thread, do nothing
                        task.value as E
                    }
                    null -> {
                        throw IllegalStateException("null can't happen")
                    }
                    else -> { // element for enqueue
                        if (!enqueue) {
                            throw IllegalStateException("Enqueue ($task) can't be here, we are dequeueing!")
                        }
                        enqueueSequentially(task as E)
                        task
                    }
                }
                helpOthersAndReleaseLock()
                return value
            }
            else {
                val task = tasksForCombiner[i].value
                if (task is Result<*>) {
                    tasksForCombiner[i].value = null
                    return task as E
                }
            }
        }
    }

    private fun helpOthersAndReleaseLock() {
        for (i in 0..TASKS_FOR_COMBINER_SIZE - 1) {
            when(val item = tasksForCombiner[i].value) {
                is Dequeue -> {
                    val element = dequeueSequentially()
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
                    enqueueSequentially(item as E)
                    tasksForCombiner[i].value = Result(item)
                }
            }
        }

        if (!combinerLock.compareAndSet(true, false)) {
            throw IllegalStateException("Impossible: the lock was not occupied")
        }
    }

    override fun dequeue(): E? {
        // 1.  Try to become a combiner by
        //     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // 2a. On success, apply this operation and help others by traversing
        //     `tasksForCombiner`, performing the announced operations, and
        //      updating the corresponding cells to `Result`.
        // 2b. If the lock is already acquired, announce this operation in
        //     `tasksForCombiner` by replacing a random cell state from
        //      `null` with `Dequeue`. Wait until either the cell state
        //      updates to `Result` (do not forget to clean it in this case),
        //      or `combinerLock` becomes available to acquire.

        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                val value = dequeueSequentially()
                helpOthersAndReleaseLock()
                return value
            }
            else {
                val i = randomCellIndex()
                if (tasksForCombiner[i].compareAndSet(null, Dequeue)) {
                    return waitForDequeueResultOrLock(i)
                }
                else { // the cell is occupied
                    continue
                }
            }
        }

    }

    private fun dequeueSequentially(): E? = queue.removeFirstOrNull()

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