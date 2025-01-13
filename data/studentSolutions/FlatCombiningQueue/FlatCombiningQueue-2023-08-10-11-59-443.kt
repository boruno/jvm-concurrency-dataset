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

        val success = tryToEnqueue(element)
        if (!success) {
            // announce this operation in `tasksForCombiner` by replacing a random cell state from `null` with the element
            var randomIdx = randomCellIndex()
            while (!tasksForCombiner.compareAndSet(randomIdx, null, element)) {
                randomIdx = randomCellIndex()
            }

            // Wait until...
            while (true) {
                // ...either the cell state updates to `Result`
                if (tasksForCombiner.get(randomIdx) is Result<*>) {
                    tasksForCombiner.set(randomIdx, null)
                    return
                }

                // ...or `combinerLock` becomes available to acquire
                val isSuccessfulEnqueue = tryToEnqueue(element)
                if (isSuccessfulEnqueue) {
                    tasksForCombiner.set(randomIdx, null)
                    return
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
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
        val result = tryToDequeue()
        // If the lock is already acquired...
        if (result == null) {
            // ...announce this operation in `tasksForCombiner` by replacing a random cell state from `null` with `Dequeue`
            var randomIdx = randomCellIndex()
            while (!tasksForCombiner.compareAndSet(randomIdx, null, Dequeue)) {
                randomIdx = randomCellIndex()
            }

            // Wait until...
            while (true) {
                // ...either the cell state updates to `Result`
                val task = tasksForCombiner.get(randomIdx)
                if (task is Result<*>) {
                    tasksForCombiner.set(randomIdx, null)
                    return task.value as E?
                }

                // ...or `combinerLock` becomes available to acquire
                val dequeued = tryToDequeue()
                if (dequeued != null) {
                    tasksForCombiner.set(randomIdx, null)
                    return dequeued
                }
            }
        }

        return result
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.length())

    // Try to become a combiner by changing `combinerLock` from `false` (unlocked) to `true` (locked)
    private fun tryToEnqueue(element: E): Boolean =
        if (combinerLock.compareAndSet(false, true)) {
            // On success, apply this operation
            queue.addLast(element)
            helpToCompleteTasks()

            true
        } else false

    // Try to become a combiner by changing `combinerLock` from `false` (unlocked) to `true` (locked)
    private fun tryToDequeue(): E? = if (combinerLock.compareAndSet(false, true)) {
        // On success, apply this operation
        val result = queue.firstOrNull()
        helpToCompleteTasks()

        result
    } else null

    @Suppress("UNCHECKED_CAST")
    private fun helpToCompleteTasks() {
        // and help others by traversing `tasksForCombiner`...
        val taskCount = tasksForCombiner.length()
        for (idx in 0..taskCount) {
            val task = tasksForCombiner.get(idx)

            // ...performing the announced operations, and updating the corresponding cells to `Result`.
            if (task != null) {
                val result = Result(task)
                val completeTask = tasksForCombiner.compareAndSet(idx, task, result)

                if (task !is Dequeue && completeTask) {
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