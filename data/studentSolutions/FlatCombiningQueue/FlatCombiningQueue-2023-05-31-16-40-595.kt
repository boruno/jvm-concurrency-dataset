//package day2

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        while (true) {
            // TODO: Make this code thread-safe using the flat-combining technique.
            // TODO: 1.  Try to become a combiner by
            // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
            if (combinerLock.compareAndSet(false, true)) {
                // TODO: 2a. On success, apply this operation and help others by traversing
                // TODO:     `tasksForCombiner`, performing the announced operations, and
                // TODO:     updating the corresponding cells to `PROCESSED`.
                processTasks()

                queue.addLast(element)
                combinerLock.compareAndSet(true, false)
                return
            } else {
                // TODO: 2b. If the lock is already acquired, announce this operation in
                // TODO:     `tasksForCombiner` by replacing a random cell state from
                // TODO:     `null` to the element. Wait until either the cell state
                // TODO:     updates to `PROCESSED` (do not forget to clean it in this case),
                // TODO:     or `combinerLock` becomes available to acquire.
                val taskCell = randomCellIndex().let { tasksForCombiner[it] }

                val task = EnqueueTask(element)
                if (!taskCell.compareAndSet(null, task)) continue

                repeat(TASK_WAIT_CYCLES) {
                    if (taskCell.compareAndSet(PROCESSED_ENQUEUE, null)) return
                }

                while (true) {
                    when {
                        taskCell.compareAndSet(PROCESSED_ENQUEUE, null) -> return
                        taskCell.compareAndSet(task, null) -> break
                    }
                }
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            // TODO: Make this code thread-safe using the flat-combining technique.
            // TODO: 1.  Try to become a combiner by
            // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
            if (combinerLock.compareAndSet(false, true)) {
                // TODO: 2a. On success, apply this operation and help others by traversing
                // TODO:     tasksForCombiner`, performing the announced operations, and
                // TODO:     updating the corresponding cells to `PROCESSED`.
                processTasks()

                val result = queue.removeFirstOrNull()
                combinerLock.compareAndSet(true, false)
                return result
            } else {
                // TODO: 2b. If the lock is already acquired, announce this operation in
                // TODO:     `tasksForCombiner` by replacing a random cell state from
                // TODO:     `null` to `DEQUE_TASK`. Wait until either the cell state
                // TODO:     updates to `PROCESSED` (do not forget to clean it in this case),
                // TODO:     or `combinerLock` becomes available to acquire.
                val taskCell = randomCellIndex().let { tasksForCombiner[it] }

                if (!taskCell.compareAndSet(null, DEQUEUE_TASK)) continue

                repeat(TASK_WAIT_CYCLES) {
                    val task = taskCell.value

                    if (task is ProcessedDequeue && taskCell.compareAndSet(task, null)) {
                        @Suppress("UNCHECKED_CAST")
                        return task.result as E
                    }
                }

                while (true) {
                    val task = taskCell.value

                    when {
                        task is ProcessedDequeue && taskCell.compareAndSet(task, null) -> {
                            @Suppress("UNCHECKED_CAST")
                            return task.result as E
                        }
                        taskCell.compareAndSet(DEQUEUE_TASK, null) -> break
                    }
                }
            }
        }
    }

    private fun processTasks() {
        for (i in 0 until tasksForCombiner.size) {
            val taskCell = tasksForCombiner[i]

            when (val task = taskCell.value) {
                DEQUEUE_TASK -> {
                    val result = queue.removeFirstOrNull()
                    if (taskCell.compareAndSet(task, ProcessedDequeue(result))) {
                        result?.let { queue.addFirst(it) }
                    }
                }

                is EnqueueTask -> {
                    if (taskCell.compareAndSet(task, PROCESSED_ENQUEUE)) {
                        @Suppress("UNCHECKED_CAST")
                        queue.addLast(task.element as E)
                    }
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private data class EnqueueTask(val element: Any?)

    private data class ProcessedDequeue(val result: Any?)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!
private const val TASK_WAIT_CYCLES = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private val DEQUEUE_TASK = Any()

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED_ENQUEUE = Any()
