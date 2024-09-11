package day2

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    @Suppress("UNCHECKED_CAST")
    override fun enqueue(element: E) {
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // TODO: 2a. On success, apply this operation and help others by traversing
        // TODO:     `tasksForCombiner`, performing the announced operations, and
        // TODO:      updating the corresponding cells to `PROCESSED`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` to the element. Wait until either the cell state
        // TODO:      updates to `PROCESSED` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        if (combinerLock.compareAndSet(false, true)) {
            queue.addLast(element)
            processAllTasks()
            combinerLock.value = false
            return
        }

        while (true) {
            val i = randomCellIndex()
            if (tasksForCombiner[i].compareAndSet(null, EnqueueTask(element))) continue
            while (true) {
                val task = tasksForCombiner[i].value
                if (task is Processed) {
                    tasksForCombiner[i].value = null
                    return
                }
                tryToProcessAllTasks()
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
        // TODO:      updating the corresponding cells to `PROCESSED`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` to `DEQUE_TASK`. Wait until either the cell state
        // TODO:      updates to `PROCESSED` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        if (combinerLock.compareAndSet(false, true)) {
            val res = queue.removeFirstOrNull()
            processAllTasks()
            combinerLock.value = false
            return res
        }
        while (true) {
            val i = randomCellIndex()
            if (tasksForCombiner[i].compareAndSet(null, DequeTask())) continue
            while (true) {
                val task = tasksForCombiner[i].value
                if (task is Processed) {
                    tasksForCombiner[i].value = null
                    return task.result as E?
                }
                tryToProcessAllTasks()
            }
        }
    }

    private fun tryToProcessAllTasks() {
        if (combinerLock.compareAndSet(false, true)) {
            processAllTasks()
            combinerLock.value = false
        }
    }

    private fun processAllTasks() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[i].value
            if (task != null) {
                when (task) {
                    is DequeTask -> {
                        val res = queue.removeFirstOrNull()
                        tasksForCombiner[i].value = Processed(res)
                    }
                    is EnqueueTask -> {
                        queue.addLast(task.element as E)
                        tasksForCombiner[i].value = Processed()
                    }
                    else -> {}
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
private val DEQUE_TASK = Any()

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED = Any()

class DequeTask()

data class EnqueueTask(val element: Any?)

data class Processed(val result: Any? = null)