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
        var randomIndex: Int? = null
        var installed = false
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                if (randomIndex != null) {
                    when (val v = tasksForCombiner[randomIndex].value) {
                        is Result<*> -> {
                            tasksForCombiner[randomIndex].value = null
                            return
                        }
                    }
                }
                queue.addLast(element)
                helpOthers()
                combinerLock.compareAndSet(true, false)
                return
            }
            if (randomIndex == null) {
                randomIndex = randomCellIndex()
            }
            if (!installed) {
                if (!tasksForCombiner[randomIndex].compareAndSet(null, element)) {
                    randomIndex = randomCellIndex()
                    continue
                }
                installed = true
            }
            when (val v = tasksForCombiner[randomIndex].value) {
                is Result<*> -> {
                    tasksForCombiner[randomIndex].compareAndSet(v, null)
                    return
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
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
        var randomIndex: Int? = null
        var installed = false
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                if (randomIndex != null) {
                    when (val v = tasksForCombiner[randomIndex].value) {
                        is Result<*> -> {
                            tasksForCombiner[randomIndex].value = null
                            return v.value as E?
                        }
                        is Dequeue -> {}
                        else -> {
                            tasksForCombiner[randomIndex].value = null
                            return v as E?
                        }
                    }
                }
                val result = queue.removeFirstOrNull()
                helpOthers()
                combinerLock.compareAndSet(true, false)
                return result
            }
            if (randomIndex == null) {
                randomIndex = randomCellIndex()
            }
            if (!installed) {
                if (!tasksForCombiner[randomIndex].compareAndSet(null, Dequeue)) {
                    randomIndex = randomCellIndex()
                    continue
                }
                installed = true
            }
            when (val v = tasksForCombiner[randomIndex].value) {
                is Result<*> -> {
                    tasksForCombiner[randomIndex].compareAndSet(v, null)
                    return v.value as E?
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun helpOthers() {
        for (i in 0 until tasksForCombiner.size) {
            val v = tasksForCombiner[i].value
            val result = when (v) {
                null -> continue
                is Result<*> -> continue
                is Dequeue -> {
                    Result(queue.removeFirstOrNull())
                }
                else -> {
                    queue.addLast(v as E)
                    Result(v)
                }
            }
            tasksForCombiner[i].compareAndSet(v, result)
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