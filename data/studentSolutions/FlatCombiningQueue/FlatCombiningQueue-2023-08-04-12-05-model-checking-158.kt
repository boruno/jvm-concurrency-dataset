package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun tryLock(): Boolean {
        return combinerLock.compareAndSet(expect = false, update = true)
    }

    private fun releaseLock(): Boolean {
        return combinerLock.compareAndSet(expect = true, update = false)
    }

    private fun helpOthers() {
        for (index in 0 until tasksForCombiner.size) {
            when (val task = tasksForCombiner[index].value) {
                null -> {}
                is Result<*> -> {}
                is Dequeue -> {
                    val result = Result(queue.removeFirstOrNull())
                    tasksForCombiner[index].getAndSet(result)
                }
                else -> {
                    val element = task as E
                    queue.addLast(element)
                    tasksForCombiner[index].getAndSet(Result(element))
                }
            }
        }
    }

    private fun publishTask(task: Any): Int {
        while (true) {
            val index = randomCellIndex()
            if (tasksForCombiner[index].compareAndSet(null, task)) {
                return index
            }
        }
    }

    private fun checkResult(index: Int): Result<E>? {
        return when (val result = tasksForCombiner[index].value) {
            is Result<*> -> {
                tasksForCombiner[index].compareAndSet(result, null)
                result as Result<E>
            }

            else -> null
        }
    }

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

        if (tryLock()) {
            queue.addLast(element)
            helpOthers()
            releaseLock()
        } else {
            val index = publishTask(element as Any)
            while (true) {
                checkResult(index)?.let {
                    return
                }
                if (tryLock()) {
                    if (checkResult(index) == null) {
                        queue.addLast(element)
                    }
                    helpOthers()
                    releaseLock()
                    return
                }
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
        if (tryLock()) {
            val element = queue.removeFirstOrNull()
            helpOthers()
            releaseLock()
            return element
        } else {
            val index = publishTask(Dequeue)
            while (true) {
                when (val result = tasksForCombiner[index].value) {
                    is Result<*> -> {
                        tasksForCombiner[index].getAndSet(null)
                        return result.value as E?
                    }
                }
                if (tryLock()) {
                    val element = checkResult(index)?.value ?: run {
                        queue.removeFirstOrNull()
                    }
                    helpOthers()
                    releaseLock()
                    return element
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
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)