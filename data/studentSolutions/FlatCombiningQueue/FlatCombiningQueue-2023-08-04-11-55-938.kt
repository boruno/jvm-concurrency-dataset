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
                if (operationIndex == null || tasksForCombiner[operationIndex].compareAndSet(element, null)) {
                    queue.addLast(element)
                } else {
                    val result = tasksForCombiner[operationIndex].value as Result<*>
                    tasksForCombiner[operationIndex].compareAndSet(result, null)
                }
                processCombinerTasks()
                resignCombiner()
                return
            }

            if (operationIndex == null) operationIndex = tryAnnounceEnqueue(element)
        }
        val result = tasksForCombiner[operationIndex].value as Result<*>
        tasksForCombiner[operationIndex].compareAndSet(result, null)
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
                val result =
                    if (operationIndex == null || tasksForCombiner[operationIndex].compareAndSet(Dequeue, null)) {
                        queue.removeFirstOrNull()
                    } else {
                        val r = (tasksForCombiner[operationIndex].value as Result<*>)
                        tasksForCombiner[operationIndex].compareAndSet(r, null)
                        r
                    } as E?
                processCombinerTasks()
                resignCombiner()
                return result
            }
            if (operationIndex == null) operationIndex = tryAnnounceDequeue()
        }
        val result = tasksForCombiner[operationIndex].value as Result<*>
        tasksForCombiner[operationIndex].compareAndSet(result, null)
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
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[i].value ?: continue
            if (task == Dequeue) tasksForCombiner[i].compareAndSet(task, Result(queue.removeFirstOrNull()))
            else {
                queue.addLast(task as E)
                tasksForCombiner[i].compareAndSet(task, Result(task))
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