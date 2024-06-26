package day2

import day1.*
import kotlinx.atomicfu.*
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.util.concurrent.*

class FlatCombiningQueue<E: Any> : Queue<E> {

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
        performTask(EnqueueTaskRequest(element))
    }

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
        return performTask(DequeueTaskRequest)
    }

    @Suppress("UNCHECKED_CAST")
    private fun performTask(task: CombinerTask): E? {
        // in case we end up asking for help, we want to do that in a consistent combiner task request array cell
        var tasksForCombinerIdx: Int? = null

        // acquire the combiner lock before doing anything directly
        while (true) {
            // try acquiring the combiner lock; if successful, exit the spinlock
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                // if we already took a seat in the combiner task request array, we should free it first
                if (tasksForCombinerIdx != null) {
                    tasksForCombiner[tasksForCombinerIdx].value = noOperationTask
                }
                break
            }

            // take a seat in the combiner task request array if we haven't done that yet
            if (tasksForCombinerIdx == null) {
                // it's possible we will attempt to take already occupied cells at first,
                // so we might have to try several times
                while (true) {
                    val potentialIdx = randomCellIndex()
                    if (tasksForCombiner[potentialIdx].compareAndSet(noOperationTask, task)) {
                        tasksForCombinerIdx = potentialIdx
                        break
                    }
                }
            }

            // check whether the combiner thread has helped us out
            val potentialTaskResult = tasksForCombiner[tasksForCombinerIdx!!].value
            // if combiner task request array still contains our task, we continue the wait
            if (potentialTaskResult == task) continue
            return when {
                // if enqueue task was completed as enqueue by the combiner thread, we're done
                task is EnqueueTaskRequest && potentialTaskResult is EnqueueTaskProcessed -> {
                    tasksForCombiner[tasksForCombinerIdx].value = noOperationTask
                    // this null doesn't really mean anything,
                    // but it's easier to unify the logic of the internal implementation if it's there
                    null
                }
                // if dequeue task was completed as dequeue by the combiner thread, we're also done
                task is DequeueTaskRequest && potentialTaskResult is DequeueTaskProcessed -> {
                    tasksForCombiner[tasksForCombinerIdx].value = noOperationTask
                    potentialTaskResult.element as E?
                }
                // all other task-result combinations are erroneous
                else -> throw IllegalStateException("combiner thread has provided incorrect results for another thread")
            }
        }

        // perform our own task first
        val taskResult = when (task) {
            is EnqueueTaskRequest -> enqueueSequentially(task.element as E)
            is DequeueTaskRequest -> dequeueSequentially()
            else -> throw IllegalArgumentException("illegal CombinerTask passed to performTask")
        }

        // pass through the combiner task request array once and help out whoever you find
        for (i in 0 until tasksForCombiner.size) {
            val combinerTaskRef = tasksForCombiner[i]
            when (val combinerTask = combinerTaskRef.value) {
                // ignore no-operation task "requests"
                noOperationTask -> continue
                // ignore yet-unfetched results of already completed tasks
                is EnqueueTaskProcessed, is DequeueTaskProcessed -> continue
                // perform enqueue for another thread
                is EnqueueTaskRequest -> {
                    enqueueSequentially(combinerTask.element as E)
                    combinerTaskRef.value = EnqueueTaskProcessed
                }
                // perform dequeue for another thread
                is DequeueTaskRequest -> {
                    val element = dequeueSequentially()
                    combinerTaskRef.value = DequeueTaskProcessed(element)
                }
            }
        }

        // release the combiner lock before finishing the task
        combinerLock.value = false
        // return the result of our own task
        return taskResult
    }

    private fun enqueueSequentially(element: E): Nothing? {
        queue.addLast(element)
        // this null doesn't really mean anything,
        // but it's easier to unify the logic of the internal implementation if it's there
        return null
    }

    private fun dequeueSequentially(): E? {
        return queue.removeFirstOrNull()
    }

    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<CombinerTask?>(TASKS_FOR_COMBINER_SIZE)

    private fun randomCellIndex(): Int = ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private sealed interface CombinerTask
    private val noOperationTask = null
    private sealed interface EnqueueTask: CombinerTask
    private class EnqueueTaskRequest(val element: Any): EnqueueTask
    private object EnqueueTaskProcessed: EnqueueTask
    private sealed interface DequeueTask: CombinerTask
    private object DequeueTaskRequest: DequeueTask
    private class DequeueTaskProcessed(val element: Any?): DequeueTask

    private companion object {
        const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!
    }

}