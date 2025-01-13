//package day4

import Queue
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

/**
 * vr locked = false
 * fun tryLock = cas(lock, false, true)
 *
 * fun unlocked {locked =false}
 *
 */

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun tryLock() = combinerLock.compareAndSet(expect = false, update = true)

    private fun helper() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val cell = tasksForCombiner[i]
            val task = cell.value as? Result<*>

            when  {
                task == null -> continue
                task.value == Dequeue -> {
                    val result = Result(queue.removeFirstOrNull())
                    cell.compareAndSet(Dequeue, result)
                }
                else -> {
                    queue.addLast(task.value as E)
                    val result = Result(task.value)
                    cell.compareAndSet(task.value, result)
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
        if (tryLock()) {
            queue.addLast(element)
            helper()
            combinerLock.compareAndSet(true, update = false)
        } else {
            val index = randomCellIndex()
            val result = Result(element)
            if (tasksForCombiner[index].compareAndSet(null, result)) {
                while (true) {
                    if (tryLock()) {
                        val result = tasksForCombiner[index].value as Result<*>
                        if (result.value == null) queue.addLast(element)
                        tasksForCombiner[index].compareAndSet(result, null)
                        helper()
                        combinerLock.compareAndSet(true, update = false)
                        return
                    } else {
                        val curValue = tasksForCombiner[index].value as? Result<*> ?: continue
                        if (tasksForCombiner[index].compareAndSet(curValue, null)) return
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
        if (tryLock()) {
            val res = queue.removeFirstOrNull()
            helper()
            combinerLock.compareAndSet(true, update = false)
            return res
        } else {
            val index = randomCellIndex()
            if (tasksForCombiner[index].compareAndSet(null, Result(Dequeue))) {
                while (true) {
                    if (tryLock()) {
                        val curValue = tasksForCombiner[index].value as Result<*>
                        val returnedValue =
                            if (curValue.value == null) queue.removeFirstOrNull() else curValue.value as E?
                        tasksForCombiner[index].compareAndSet(curValue, null)
                        helper()
                        combinerLock.compareAndSet(true, update = false)
                        return returnedValue
                    } else {
                        val curValue = tasksForCombiner[index].value as? Result<*> ?: continue
                        if (tasksForCombiner[index].compareAndSet(curValue.value, null)) {
                            return curValue.value as E?
                        }
                    }
                }
            }
        }
        return null
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