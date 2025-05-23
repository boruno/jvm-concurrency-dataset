//package day4

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
        if (tryBecomeCombiner()) {
            queue.addLast(element)
            processCombinerTasks()
            resignCombiner()
            return
        }

        // announce operation and wait for completion or try to become combiner
        var operationIndex = tryAnnounceEnqueue(element)
        while (operationIndex == null || tasksForCombiner[operationIndex].value !is Result<*>) {
            if (tryBecomeCombiner()) {
                operationIndex?.also { tasksForCombiner[it].value = null }
                queue.addLast(element)
                processCombinerTasks()
                resignCombiner()
                return
            }
            if (operationIndex == null) operationIndex = tryAnnounceEnqueue(element)
        }
    }

    private fun tryAnnounceEnqueue(element: E): Int? =
        randomCellIndex().let {
            if (tasksForCombiner[it].compareAndSet(null, element)) it
            else null
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
        if (tryBecomeCombiner()) {
            val result = queue.removeFirstOrNull()
            processCombinerTasks()
            resignCombiner()
            return result
        }

        // announce operation and wait for completion or try to become combiner
        var operationIndex = tryAnnounceDequeue()
        while (operationIndex == null || tasksForCombiner[operationIndex].value !is Result<*>) {
            if (tryBecomeCombiner()) {
                operationIndex?.also { tasksForCombiner[it].value = null }
                val result = queue.removeFirstOrNull()
                processCombinerTasks()
                resignCombiner()
                return result
            }
            if (operationIndex == null) operationIndex = tryAnnounceDequeue()
        }
        val result = tasksForCombiner[operationIndex].value as Result<*>
        return result.value as E?
    }

    private fun tryAnnounceDequeue(): Int? =
        randomCellIndex().let {
            if (tasksForCombiner[it].compareAndSet(null, Dequeue)) it
            else null
        }

    private fun tryBecomeCombiner(): Boolean = combinerLock.compareAndSet(false, true)
    private fun resignCombiner(): Boolean = combinerLock.compareAndSet(true, false)
    private fun processCombinerTasks() {
        while (true) {
            val index = randomCellIndex()
            val task = tasksForCombiner[index].value ?: return
            if (task == Dequeue) tasksForCombiner[index].compareAndSet(task, Result(queue.removeFirstOrNull()))
            else {
                queue.addLast(task as E)
                tasksForCombiner[index].compareAndSet(task, Result(task))
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