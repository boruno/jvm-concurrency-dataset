//package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private inline fun withLock(action: () -> Unit): Boolean = if (combinerLock.compareAndSet(false, true)) {
        try {
            action()
            true
        } finally {
            combinerLock.value = false
        }
    } else {
        false
    }

    private fun findSell(element: Any?): Int {
        var sell: AtomicRef<Any?>?
        while (true) {
            val index = randomCellIndex()
            sell = tasksForCombiner[index]
            if (sell.compareAndSet(null, element)) return index
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
        withLock {
            queue.addLast(element)
            help()
            return
        }

        val index = findSell(element)
        val sell = tasksForCombiner[index]
        while (true) {
            val value = sell.value
            when {
                value == null -> return
                withLock {
                    if (sell.compareAndSet(element, null)) {
                        queue.addLast(element)
                    }

                    help()
                    return
                } -> {}
                else -> {}
            }
        }
//        var sell: AtomicRef<Any?>? = null
//        while (true) {
//            when {
//                combinerLock.compareAndSet(false, true) -> {
//                    try {
//                        if (sell != null) {
//                            val value = sell.value
//                            if (value == null || sell.compareAndSet(element, null)) {
//                                queue.addLast(element)
//                            }
//                        } else {
//                            queue.addLast(element)
//                        }
//
//                        help()
//                        return
//                    } finally {
//                        combinerLock.value = false
//                    }
//                }
//
//                sell == null -> {
//                    val index = randomCellIndex()
//                    sell = tasksForCombiner[index]
//                    if (sell.compareAndSet(null, element)) continue else sell = null
//                }
//
//                else -> {
//                    if (sell.value == null) {
//                        return
//                    }
//                }
//            }
//        }
    }

    private fun help() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val sell = tasksForCombiner[i]
            val value = sell.value
            when {
                value == null -> continue
                value is Dequeue -> {
                    sell.value = Result(queue.removeFirstOrNull())
                }

                else -> {
                    queue.addLast(value as E)
                    sell.value = null
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
        withLock {
            val result = queue.removeFirstOrNull()
            help()
            return result
        }

        val index = findSell(Dequeue)
        val sell = tasksForCombiner[index]
        while (true) {
            val value = sell.value
            when {
                value is Result<*> -> {
                    sell.value = null
                    return value as E?
                }
                withLock {
                    val result = if (sell.compareAndSet(Dequeue, null)) {
                        queue.removeFirstOrNull()
                    } else {
                        val result = sell.value as Result<*>
                        sell.value = null
                        result.value as E?
                    }

                    help()
                    return result
                } -> {}
                else -> {}
            }
        }
//        var result: E? = null
//        var sell: AtomicRef<Any?>? = null
//        while (true) {
//            when {
//                combinerLock.compareAndSet(false, true) -> {
//                    try {
//                        if (sell == null) {
//                            result = queue.removeFirstOrNull()
//                        } else {
//                            val value = sell.value
//                            if (value is Dequeue) {
//                                sell.compareAndSet(value, null)
//                            } else if (value is Result<*>) {
//                                result = value.value as E
//                                sell.compareAndSet(value, null)
//                            } else {
//                                result = queue.removeFirstOrNull()
//                            }
//                        }
//
//                        help()
//                        return result
//                    } finally {
//                        combinerLock.value = false
//                    }
//                }
//
//                sell == null -> {
//                    val index = randomCellIndex()
//                    sell = tasksForCombiner[index]
//                    if (sell.compareAndSet(null, Dequeue)) continue else sell = null
//                }
//
//                else -> {
//                    val value = sell.value
//                    if (value is Result<*>) {
//                        sell.value = null
//                        return value.value as E
//                    }
//                }
//            }
//        }

//        return result
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