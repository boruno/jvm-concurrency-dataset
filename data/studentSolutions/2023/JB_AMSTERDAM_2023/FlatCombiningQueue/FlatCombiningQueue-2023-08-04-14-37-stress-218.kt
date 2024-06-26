package day4

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
        var arrIdx: Int = -1
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                if (arrIdx != -1) {
                    if (tasksForCombiner[arrIdx].getAndSet(null) !is Result<*>) {
                        queue.addLast(element)
                    }
                } else {
                    queue.addLast(element)
                }
                for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
                    if (tasksForCombiner[i].value == Dequeue) {
                        tasksForCombiner[i].compareAndSet(Dequeue, Result(queue.removeFirstOrNull()))
                    }
                    if (tasksForCombiner[i].value != null && tasksForCombiner[i].value !is Result<*>) {
                        queue.addLast(tasksForCombiner[i].value as E)
                        tasksForCombiner[i].compareAndSet(tasksForCombiner[i].value, Result(tasksForCombiner[i].value))
                    }
                }
                combinerLock.compareAndSet(true, false)
                break
            } else {
                /////
                if (arrIdx == -1) {
                    arrIdx = randomCellIndex()
                    if (tasksForCombiner[arrIdx].compareAndSet(null, element)) {
                        continue
                    }
                    else {
                        arrIdx = -1
                        continue
                    }
                } else {
                    val curCellState = tasksForCombiner[arrIdx].value
                    if (curCellState is Result<*>) {
                        tasksForCombiner[arrIdx].compareAndSet(curCellState, null)
                        break
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
        var arrIdx: Int? = null
        var result: E? = null
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                if (arrIdx != null) {
                    val curCell = tasksForCombiner[arrIdx].getAndSet(null)
                    if (curCell is Result<*>) {
                        result = curCell.value as E
                    } else {
                        result = queue.removeFirstOrNull()
                    }
                } else {
                    result = queue.removeFirstOrNull()
                }
                for (i in 0..TASKS_FOR_COMBINER_SIZE - 1) {
                    if (tasksForCombiner[i].value == Dequeue) {
                        tasksForCombiner[i].compareAndSet(Dequeue, Result(queue.removeFirstOrNull()))
                    }
                    if (tasksForCombiner[i].value != null && tasksForCombiner[i].value !is Result<*>) {
                        queue.addLast(tasksForCombiner[i].value as E)
                        tasksForCombiner[i].compareAndSet(tasksForCombiner[i].value, Result(tasksForCombiner[i].value))
                    }
                }
                combinerLock.compareAndSet(true, false)
                break
            } else {
                /////
                if (arrIdx == null) {
                    arrIdx = randomCellIndex()
                    if (tasksForCombiner[arrIdx].compareAndSet(null, Dequeue)) {
                        continue
                    } else {
                        val curCellState = tasksForCombiner[arrIdx].value
                        if (curCellState is Result<*>) {
                            tasksForCombiner[arrIdx].compareAndSet(curCellState, null)
                            result = curCellState.value as E
                            break
                        }
                    }
                }
            }
        }
        return result
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