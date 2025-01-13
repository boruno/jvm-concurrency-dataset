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
        var i: Int? = null
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                i?.let {
                    val task = tasksForCombiner[it].value
                    freeSlot(it)
                    task as? Result<*>
                } ?: queue.addLast(element)
                executeTasks()
                return
            } else if (i == null) {
                i = randomCellIndex()
                if (!tasksForCombiner[i].compareAndSet(null, element)) {
                    i = null
                }
            } else {
                val e = tasksForCombiner[i].value
                (e as? Result<*>)?.let { return }
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
        var i: Int? = null
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                val result = i?.let {
                    val task = tasksForCombiner[it].value
                    freeSlot(it)
                    (task as? Result<*>)?.value
                } ?: queue.removeFirstOrNull()
                executeTasks()
                return result as E?
            } else if (i == null) {
                i = randomCellIndex()
                if (!tasksForCombiner[i].compareAndSet(null, Dequeue)) {
                    i = null
                }
            } else {
                val e = tasksForCombiner[i].value
                (e as? Result<*>)?.let { return it.value as E? }
            }
        }
    }

    fun freeSlot(i: Int?) {
        i?.let { tasksForCombiner[i].value == null }
    }

    fun executeTasks() {
        try {
            for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
                val t = tasksForCombiner[i].value
                t?.let {
                    when (it) {
                        is Result<*> -> { } // do nothing
                        Dequeue -> {
                            val e = queue.removeFirstOrNull()
                            tasksForCombiner[i].value = Result(e)
                        }
                        else -> {
                            queue.addLast(it as E)
                            tasksForCombiner[i].value= Result(it)
                        }
                    }
                }
            }
        } finally {
            combinerLock.compareAndSet(true, false)
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