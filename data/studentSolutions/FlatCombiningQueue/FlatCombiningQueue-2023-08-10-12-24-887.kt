//package day4

import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    fun helpOthers() {
        for (i in 0 until tasksForCombiner.length()) {
            val elem = tasksForCombiner.get(i)
            when (elem) {
                Dequeue -> dequeue()
                null -> continue
                else -> {
                    if (tasksForCombiner.compareAndSet(i, elem, Result(elem))) queue.addLast(elem as E)
                }
            }
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
//        queue.addLast(element)
        if (combinerLock.compareAndSet(false, true)) {
            queue.addLast(element)
            helpOthers()
            combinerLock.set(false)
        } else {
            while (true) {
                val index = randomCellIndex()
                if (tasksForCombiner.compareAndSet(index, null, element)) {
                    while (true) {
                        if (tasksForCombiner.compareAndSet(index, Result(element), null)) return
                        if (combinerLock.compareAndSet(false, true)) {
                            if (tasksForCombiner.compareAndSet(index, element, null)) {
                                enqueue(element)
                                combinerLock.set(false)
                                return
                            }

                        }
                    }
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
//        return queue.removeFirstOrNull()
        if (combinerLock.compareAndSet(false, true)) {
            val elem = queue.removeFirstOrNull()
            helpOthers()
            combinerLock.set(false)
            return elem
        } else {
            while (true) {
                val index = randomCellIndex()
                if (tasksForCombiner.compareAndSet(index, null, Dequeue)) {
                    while (true) {
                        if (tasksForCombiner.compareAndSet(index, Result(Dequeue), null) ||
                            combinerLock.compareAndSet(false, true)
                        ) {
//                            combinerLock.set(false)
                            return dequeue()
                        }
                    }
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