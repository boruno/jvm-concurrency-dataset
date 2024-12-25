//package day4

import day1.*
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
        if (combinerLock.compareAndSet(false, true)) {
            queue.addLast(element)
            help()
            combinerLock.value = false
        } else {
            while (true) {
                val randomCellIndex = randomCellIndex()
                val task = tasksForCombiner[randomCellIndex].value
                if (task == null) {
                    if (tasksForCombiner[randomCellIndex].compareAndSet(null, element)) {
                        while (true) {
                            val result = tasksForCombiner[randomCellIndex].value
                            if (result is EnqueueResult)
                            {
                                if (tasksForCombiner[randomCellIndex].compareAndSet(result, null))
                                    return
                            }
                            if (combinerLock.compareAndSet(false, true)) {
                                if (tasksForCombiner[randomCellIndex].compareAndSet(element, null))
                                {
                                    queue.addLast(element)
                                    combinerLock.value = false
                                    return
                                }
                                combinerLock.value = false
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

        if (combinerLock.compareAndSet(false, true)) {
            val result = queue.removeFirstOrNull()
            help()
            combinerLock.value = false
            return result
        } else {
            while (true) {
                val randomCellIndex = randomCellIndex()
                val task = tasksForCombiner[randomCellIndex].value
                if (task == null) {
                    if (tasksForCombiner[randomCellIndex].compareAndSet(null, Dequeue)) {
                        while (true) {
                            val result = tasksForCombiner[randomCellIndex].value
                            if (result is Result<*>) {
                                if (tasksForCombiner[randomCellIndex].compareAndSet(result, null)) {
                                    return result.value as? E
                                }
                            }
                            if (combinerLock.compareAndSet(false, true)) {
                                if (tasksForCombiner[randomCellIndex].compareAndSet(Dequeue, null)) {
                                    val result = queue.removeFirstOrNull()
                                    combinerLock.value = false
                                    return result
                                }
                                combinerLock.value = false
                            }
                        }
                    }
                }
            }
        }
    }

    private fun help() {
        for (i in 0 until tasksForCombiner.size) {
            val task = tasksForCombiner[i].value
            if (task == null || task == Result || task == EnqueueResult)
                continue

            when (task) {
                is Dequeue -> {
                    val dequeResult = Result(queue.removeFirstOrNull())
                    tasksForCombiner[i].compareAndSet(task, dequeResult)
                }

                else -> {
                    val element = task as E
                    queue.addLast(element)
                    tasksForCombiner[i].compareAndSet(task, EnqueueResult)
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

private object EnqueueResult


// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(


    val value: V

)