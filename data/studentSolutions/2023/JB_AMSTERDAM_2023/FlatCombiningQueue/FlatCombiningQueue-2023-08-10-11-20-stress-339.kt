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
        if (tryLock()) {
            // the thread became a combiner
            // 1. complete its own operation
            queue.addLast(element)
            // help others
            processQueue()
            unlock()
        }
        else {
            putOperationToQueueAndWaitForResult(Enqueue(element))
        }
    }

    private fun <E> putOperationToQueueAndWaitForResult(operation: Any): E {
        while (true) {
            val taskIndex = randomCellIndex()
            if (tasksForCombiner.compareAndSet(taskIndex, null, operation)) {
                // put the operation to the queue, wait
                while (true) {
                    val result = tasksForCombiner.get(taskIndex)
                    if (result is Result<*>) {
                        // operation completed, clear the cell and return
                        tasksForCombiner.compareAndSet(taskIndex, result, null)
                        return result.value as E
                    }
                    // maybe can acquire the lock?
                    if (tryLock()) {
                        processQueue()
                        val result = (tasksForCombiner.get(taskIndex) as Result<*>).value as E
                        unlock()
                        return result
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
        if (tryLock()) {
            // the thread became a combiner
            // 1. complete its own operation
            return queue.removeFirstOrNull().also {
                // help others
                processQueue()
                unlock()
            }
        }
        else {
            return putOperationToQueueAndWaitForResult(Dequeue)
        }
    }

    private fun processQueue() {
        // help others by processing the queue
        for (i in 0 until tasksForCombiner.length()) {
            val task = tasksForCombiner.get(i)
            when (task) {
                is Enqueue<*> -> {
                    queue.addLast(task.value as E)
                    tasksForCombiner.compareAndSet(i, task, Result(Any()))
                }
                Dequeue -> {
                    tasksForCombiner.compareAndSet(i, task, Result(queue.removeFirstOrNull()))
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.length())

    private fun tryLock() = combinerLock.compareAndSet(false, true)

    private fun unlock() {
        combinerLock.set(false)
    }
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

private class Enqueue<E>(val value: E)

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)