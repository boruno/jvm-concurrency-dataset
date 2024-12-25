//package day4

import day1.*
import day4.Result
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

        val locked = combinerLock.compareAndSet(false, true)
        if (locked) {
            queue.addLast(element)
            completeTasks()
            combinerLock.value = false
            return
        } else {
            val i = putOp(element as Any)
            // waiter logic
            while (true) {
                if (combinerLock.compareAndSet(false, true)) {
                    // locked
                    val cell = tasksForCombiner[i].value
                    if (cell is Result<*>) {
                        tasksForCombiner[i].value = null
                        combinerLock.value = false
                        return
                    } else {
                        tasksForCombiner[i].value = null
                        queue.addLast(element)
                        completeTasks()
                        combinerLock.value = false
                        return
                    }
                } else {
                    val cell = tasksForCombiner[i].value
                    if (cell is Result<*>) {
                        tasksForCombiner[i].value = null
                        return
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

        val locked = combinerLock.compareAndSet(false, true)
        if (locked) {
            val result = queue.removeFirstOrNull()
            completeTasks()
            combinerLock.value = false
            return result
        } else {
            val i = putOp(Dequeue)
            while (true) {
                if (combinerLock.compareAndSet(false, true)) {
                    val cell = tasksForCombiner[i].value
                    // check if op is done
                    if (cell is Result<*>) {
                        val result = cell.value as E?

                        tasksForCombiner[i].value = null
                        combinerLock.value = false

                        return result
                    } else {
                        val result = queue.removeFirstOrNull()

                        tasksForCombiner[i].value = null
                        completeTasks()
                        combinerLock.value = false
                        return result
                    }
                } else {
                    val cell = tasksForCombiner[i].value
                    if (cell is Result<*>) {
                        tasksForCombiner[i].value = null
                        return cell.value as E?
                    }
                }
            }
        }
    }

    private fun putOp(op: Any): Int {
        while (true) {
            val i = randomCellIndex()
            if (tasksForCombiner[i].compareAndSet(null, op)) {
                return i
            }
        }
    }

    private fun completeTasks() {
        // try to complete tasks
        (0 until TASKS_FOR_COMBINER_SIZE).forEach { i ->
            val op = tasksForCombiner[i].value
            if (op != null) {
                if (op is Dequeue) {
                    queue.removeFirstOrNull()?.let {
                        tasksForCombiner[i].compareAndSet(op, Result(it))
                    }
                } else {
                    queue.addLast(op as E)
                    tasksForCombiner[i].compareAndSet(op, Result(null))
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)

//    private fun tryLock(underLock: () -> Unit, ): E? {
//        val locked = combinerLock.compareAndSet(false, true)
//        if (locked) {
//            underLock()
//            queue.addLast(element)
//            completeTasks()
//            combinerLock.value = false
//            return
//        } else {
//
//        }
//    }

}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V?
)