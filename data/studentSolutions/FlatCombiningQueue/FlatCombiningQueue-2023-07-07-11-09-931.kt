//package day4

import kotlinx.atomicfu.*
import java.util.concurrent.*
import kotlin.random.Random

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

        if (combinerLock.compareAndSet(expect = false, update = true)) {
            doCombine()
            return
        }

        val taskIndex = addTaskToCombiner(element)
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                queue.addLast(element)
                doCombine()
                return
            }

            if (tasksForCombiner[taskIndex].value is Result<*>) {
                tasksForCombiner[taskIndex].compareAndSet(tasksForCombiner[taskIndex].value, null)
                return
            }
        }
    }

    private fun doCombine() {
        // traverse array
        for (i in 0..TASKS_FOR_COMBINER_SIZE - 1) {
            val cellValue = tasksForCombiner[i].value
            when (cellValue) {
                null, is Result<*> -> continue
                is Dequeue -> {
                    val result = Result(queue.first())
                    tasksForCombiner[i].compareAndSet(cellValue, result)
                }
                else -> {
                    queue.addLast(cellValue as E)
                    val result = Result(cellValue as E)
                    tasksForCombiner[i].compareAndSet(cellValue, result)
                }
            }
        }
    }

    private fun addTaskToCombiner(task: Any?): Int {
        val index = randomCellIndex()
        tasksForCombiner[index].compareAndSet(null, task)
        return index
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
            val element = queue.removeFirstOrNull()
            doCombine()
            return element
        }

        val taskIndex = addTaskToCombiner(Dequeue)
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                val element = queue.removeFirstOrNull()
                doCombine()
                return element
            }

            val cellValue = tasksForCombiner[taskIndex].value
            if (cellValue is Result<*>) {
                val element = cellValue.value as E
                tasksForCombiner[taskIndex].compareAndSet(cellValue, null)
                return element
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