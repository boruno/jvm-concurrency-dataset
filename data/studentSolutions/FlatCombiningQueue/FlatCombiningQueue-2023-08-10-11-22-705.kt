//package day4

import Result
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

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
            helpOthers()
            combinerLock.set(false)
            return
        }
        while (true) {
            val randomIndex = randomCellIndex()
            if (!tasksForCombiner.compareAndSet(randomIndex, null, element)) continue
            while (true) {
                if (combinerLock.compareAndSet(false, true)) {
                    helpOthers()
                    combinerLock.set(false)
                }
                if (tasksForCombiner[randomIndex] is Result<*>) {
                    tasksForCombiner[randomIndex] = null
                    return
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun helpOthers() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            when (val state = tasksForCombiner[i]) {
                (state is Dequeue) -> {
                    tasksForCombiner.compareAndSet(i, state, Result(queue.removeFirstOrNull()))
                }
                ((state as? E) != null) -> {
                    tasksForCombiner.compareAndSet(i, state, Result(state as E))
                }
                else -> continue
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


        if (combinerLock.compareAndSet(false, true)) {
            val res = queue.removeFirstOrNull()
            helpOthers()
            combinerLock.set(false)
            return res
        }
        while (true) {
            val randomIndex = randomCellIndex()
            if (!tasksForCombiner.compareAndSet(randomIndex, null, Dequeue)) continue
            while (true) {
                if (combinerLock.compareAndSet(false, true)) {
                    helpOthers()
                    combinerLock.set(false)
                }
                val state = tasksForCombiner[randomIndex]
                if (state is Result<*>) {
                    tasksForCombiner[randomIndex] = null
                    return state.value as E
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.length())
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)