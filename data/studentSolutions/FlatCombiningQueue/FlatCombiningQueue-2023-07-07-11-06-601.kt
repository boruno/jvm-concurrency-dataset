//package day4

import Result
import kotlinx.atomicfu.*
import java.lang.IllegalStateException
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

        if (combinerLock.compareAndSet(false, true)) {
            queue.addLast(element)
            processTasks()
        } else {
            while (true) {

                var i = -1
                while (true) {
                    i = randomCellIndex()
                    if (tasksForCombiner[i].compareAndSet(null, element)) break
                }

                if (combinerLock.compareAndSet(false, true)) {
                    if (tasksForCombiner[i].compareAndSet(element, null)) {
                        enqueue(element)
                    } else {
                        tasksForCombiner[i].compareAndSet(Result(element), null)
                    }
                    processTasks()
                    return
                } else if (tasksForCombiner[i].value == Result) {
                    return
                }
            }
        }
    }

    private fun processTasks() {
        try {
            for (i in 0 until tasksForCombiner.size ) {
                val e = tasksForCombiner[i]
                val v = e.value as E
                when (e.value) {
                    null -> {} //do Nothing
                    Dequeue -> {
                        queue.removeFirstOrNull()
                        e.compareAndSet(v, Result(v))
                    }
                    else -> {
                        queue.addLast(v)
                        e.compareAndSet(v, Result(e))
                    }
                }
            }
        } finally {
            combinerLock.compareAndSet(true, false)
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
        return queue.removeFirstOrNull()
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