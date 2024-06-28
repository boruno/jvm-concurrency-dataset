package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Operation<E>?>(TASKS_FOR_COMBINER_SIZE)

    private fun writeOperationToCell(operation: Operation<E>): Int {
        while (true) {
            val i = randomCellIndex()
            if (tasksForCombiner[i].compareAndSet(null, operation)) {
                continue // acquired place in combiner
            }
        }
    }

    override fun enqueue(element: E) {
        // put our task into combiner
        val taskIndex = writeOperationToCell(Operation.Enqueue(element))
        processCombiner(taskIndex)


//        queue.addLast(element)
    }

    override fun dequeue(): E? {
        val taskIndex = writeOperationToCell(Operation.Dequeue)
        return processCombiner(taskIndex)
        // get result from the cell


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
//        return queue.removeFirstOrNull()
    }

    private fun processCombiner(taskIndex: Int): E? {
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).

        var result: E? = null

        // acquire lock
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                break
            }
        }

        // process combiner
        repeat(tasksForCombiner.size) { i ->
            val task = tasksForCombiner[i].value
                ?: return@repeat // empty cell, proceed with next task

            when (task) {
                Operation.Dequeue -> {
                    val currentResult = queue.removeFirstOrNull()
                    if (i == taskIndex) {
                        result = currentResult
                    }
                }

                is Operation.Enqueue -> {
                    queue.addLast(task.element)
                }
            }
            // cleanup task cell
            tasksForCombiner[i].value = null // only us can write to cell that is not null
        }

        // release lock
        combinerLock.value = false

        return result
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.

private sealed class Operation<out E> {
    object Dequeue: Operation<Nothing>()
    class Enqueue<E>(val element: E): Operation<E>()
}

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)