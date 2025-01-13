//package day4

import Queue
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

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
        require(element != null)
        if (combinerLock.compareAndSet(expect = false, update = true)) {
            queue.addLast(element)
            processCombinerTasks()
            combinerLock.value = false
        }
        executeTask(element)
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
        if (combinerLock.compareAndSet(expect = false, update = true)) {
            val result: E? = queue.removeFirstOrNull()
            processCombinerTasks()
            combinerLock.value = false
            return result
        }
        @Suppress("UNCHECKED_CAST")
        return executeTask(Dequeue) as E?
    }

    private fun executeTask(task: Any): Any? {
        var taskIndex: Int
        do {
            taskIndex = randomCellIndex()
        } while (!tasksForCombiner[taskIndex].compareAndSet(null, task))

        while (true) {
            val value = tasksForCombiner[taskIndex].value
            if (value is Result<*>) {
                tasksForCombiner[taskIndex].value = null
                return value.value
            }

            if (combinerLock.compareAndSet(expect = false, update = true)) {
                processCombinerTasks()
                combinerLock.value = false
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private fun processCombinerTasks() {
        for (i in 0 until tasksForCombiner.size) {
            val value = tasksForCombiner[i].value
            if (value === null || value is Result<*>) {
                continue
            }
            if (value === Dequeue) {
                val element = queue.removeFirstOrNull()
                tasksForCombiner[i].value = Result(element)
                continue
            }
            @Suppress("UNCHECKED_CAST")
            queue.addLast(value as E)
            tasksForCombiner[i].value = Result(Unit)
        }
    }
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

private object Dequeue

private class Result<V>(
    val value: V
)