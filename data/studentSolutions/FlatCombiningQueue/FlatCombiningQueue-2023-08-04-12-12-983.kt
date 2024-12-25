//package day4

import day1.Queue
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
        var cellIndex: Int? = null
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                if (cellIndex != null) {
                    tasksForCombiner[cellIndex].value = null
                    cellIndex = null
                }
                val result = queue.addLast(element)
                processCombinerTasks()
                combinerLock.value = false
                return result
            }
            if (cellIndex == null) {
                val index = randomCellIndex()
                if (!tasksForCombiner[index].compareAndSet(null, element)) {
                    continue
                }
                cellIndex = index
            }
            val value = tasksForCombiner[cellIndex].value
            if (value is Result<*>) {
                tasksForCombiner[cellIndex].value = null
                return
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
        var cellIndex: Int? = null
        while (true) {
            if (combinerLock.compareAndSet(expect = false, update = true)) {
                if (cellIndex != null) {
                    tasksForCombiner[cellIndex].value = null
                    cellIndex = null
                }
                val result = queue.removeFirstOrNull()
                processCombinerTasks()
                combinerLock.value = false
                return result
            }
            if (cellIndex == null) {
                val index = randomCellIndex()
                if (!tasksForCombiner[index].compareAndSet(null, Dequeue)) {
                    continue
                }
                cellIndex = index
            }
            val value = tasksForCombiner[cellIndex].value
            if (value is Result<*>) {
                tasksForCombiner[cellIndex].value = null
                @Suppress("UNCHECKED_CAST")
                return value.value as E?
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

    private fun processCombinerTasks() {
        for (i in 0 until tasksForCombiner.size) {
            val value = tasksForCombiner[i].value
            if (value === null) {
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

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)