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
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                enqueueUnderLock(element)
                return
            } else {
                val index = randomCellIndex()
                if (tasksForCombiner[index].compareAndSet(null, element)) {
                    while (true) {
                        if (combinerLock.compareAndSet(false, true)){
                            if (tasksForCombiner[index].compareAndSet(OK, null)) {
                                combinerLock.compareAndSet(true,false)
                                return
                            }
                            tasksForCombiner[index].compareAndSet(element, null)
                            enqueueUnderLock(element)
                            return
                        }
                    }
                }
            }
        }
    }

    private fun dequeueUnderLock(): E? {
        val res = queue.removeFirstOrNull()
        helpOthers()
        combinerLock.compareAndSet(true, false)
        return res
    }
    private fun enqueueUnderLock(element: E) {
        queue.addLast(element)
        helpOthers()
        combinerLock.compareAndSet(true, false)
    }

    private fun helpOthers() {
        for (it in 0 until tasksForCombiner.size) {
            when (val curVal = tasksForCombiner[it].value) {
                null -> continue
                is Result<*> -> continue
                Dequeue -> tasksForCombiner[it].compareAndSet(curVal, Result(queue.removeFirstOrNull()))
                else -> {
                    queue.addLast(curVal as E)
                    tasksForCombiner[it].compareAndSet(curVal, OK)
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
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                return dequeueUnderLock()
            } else {
                val index = randomCellIndex()
                if (tasksForCombiner[index].compareAndSet(null, Dequeue)) {
                    while (true) {
                        if (combinerLock.compareAndSet(false, true)) {
                            val curVal = tasksForCombiner[index].value
                            if (curVal is Result<*>) {
                                combinerLock.compareAndSet(true, false)
                                return curVal.value as? E?
                            }
                            tasksForCombiner[index].compareAndSet(Dequeue, null)
                            return dequeueUnderLock()
                        }
                        val curVal = tasksForCombiner[index].value
                        if (curVal == Dequeue) continue
                        val res = curVal as? Result<E?> ?: continue
                        tasksForCombiner[index].compareAndSet(res, null)
                        return res.value
                    }
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
private object OK

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)