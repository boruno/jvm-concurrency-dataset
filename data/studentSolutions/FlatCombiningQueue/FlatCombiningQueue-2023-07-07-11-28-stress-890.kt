package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E : Any> : Queue<E> {
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

        execute(element)
    }

    private fun storeTask(element: Any): Int {
        while (true) {
            val index = randomCellIndex()
            tasksForCombiner[index].compareAndSet(null, element)
            return index
        }
    }
    private fun execute(element: Any): Any? {
        val index = storeTask(element)

        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                try {
                    val result = getResult(index, element)
                    helpOthers()
                    return result
                } finally {
                    combinerLock.compareAndSet(true, false)
                }
            } else {
                val value = tasksForCombiner[index].value
                if (value is Result<*>) {
                    tasksForCombiner[index].compareAndSet(value, null)
                    return value.value
                }
            }
        }

    }

    private fun helpOthers() {
        var i = 0
        while (i < TASKS_FOR_COMBINER_SIZE) {
            val value = tasksForCombiner[i].value
            if (value != null) {
                tasksForCombiner[i].compareAndSet(value, executeElement(value))
            }
            i++
        }
    }

    private fun checkResult(index: Int): Result<Any?>? {
        val result = tasksForCombiner[index].value
        if (result != null) {
            tasksForCombiner[index].compareAndSet(result, null)
            return result as Result<Any?>?
        }
        return null
    }

    private fun getResult(index: Int?, element: Any): Any? {
        val result = if (index != null && !tasksForCombiner[index].compareAndSet(element, null)) {
            checkResult(index)
        } else executeElement(element)
        return result!!.value
    }

    private fun executeElement(element: Any): Result<Any?> = when (element) {
        is Dequeue -> Result(queue.firstOrNull())
        else -> {
            queue.add(element as E)
            Result(null)
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
        return execute(Dequeue) as E?
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

