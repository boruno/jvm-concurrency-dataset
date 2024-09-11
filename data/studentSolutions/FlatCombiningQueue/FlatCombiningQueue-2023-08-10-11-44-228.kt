package day4

import day1.Queue
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReferenceArray

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

//        var taskIndex: Int? = null
//
//        while (true) {
//            if (taskIndex != null) {
//                val cellState = tasksForCombiner.get(taskIndex)
//                if (cellState is Result<*>) {
//                    tasksForCombiner.set(taskIndex, null)
//                    return
//                }
//            }
//
//            if (combinerLock.compareAndSet(false, true)) {
//                if (taskIndex != null) {
//                    tasksForCombiner.set(taskIndex, null)
//                }
//
//                queue.addLast(element)
//
//                help()
//
//                combinerLock.set(false)
//                return
//            }
//
//            if (taskIndex == null) {
//                val index = randomCellIndex()
//                if (tasksForCombiner.compareAndSet(index, null, element)) {
//                    taskIndex = index
//                }
//            }
//        }

        doInFlatCombine(element as Any) { queue.addLast(it as E) }
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

//        var taskIndex: Int? = null
//
//        while (true) {
//            if (taskIndex != null) {
//                val cellState = tasksForCombiner.get(taskIndex)
//                if (cellState is Result<*>) {
//                    tasksForCombiner.set(taskIndex, null)
//                    return cellState.value as E
//                }
//            }
//
//            if (combinerLock.compareAndSet(false, true)) {
//                if (taskIndex != null) {
//                    tasksForCombiner.set(taskIndex, null)
//                }
//
//                val result = queue.removeFirstOrNull()
//
//                help()
//
//                combinerLock.set(false)
//                return result
//            }
//
//            if (taskIndex == null) {
//                val index = randomCellIndex()
//                if (tasksForCombiner.compareAndSet(index, null, Dequeue)) {
//                    taskIndex = index
//                }
//            }
//        }

        return doInFlatCombine(Dequeue) { queue.removeFirstOrNull() }.value as E
    }

    private fun doInFlatCombine(task: Any, op: (Any) -> Any?): Result<*> {
        var taskIndex: Int? = null

        while (true) {
            if (taskIndex != null) {
                val cellState = tasksForCombiner.get(taskIndex)
                if (cellState is Result<*>) {
                    tasksForCombiner.set(taskIndex, null)
                    return cellState
                }
            }

            if (combinerLock.compareAndSet(false, true)) {
                if (taskIndex != null) {
                    val cellState = tasksForCombiner.get(taskIndex)
                    if (cellState is Result<*>) {
                        tasksForCombiner.set(taskIndex, null)
                        return cellState
                    }
                    tasksForCombiner.set(taskIndex, null)
                }

                val result = op(task)

                help()

                combinerLock.set(false)
                return Result(result)
            }

            if (taskIndex == null) {
                val index = randomCellIndex()
                if (tasksForCombiner.compareAndSet(index, null, task)) {
                    taskIndex = index
                }
            }
        }
    }

    private fun help() {
        for (i in 0 until tasksForCombiner.length()) {
            val cellState = tasksForCombiner.get(i) ?: continue
            if (cellState is Dequeue) {
                val result = queue.removeFirstOrNull()
                tasksForCombiner.set(i, Result(result))
            } else {
                queue.addLast(cellState as E)
                tasksForCombiner.set(i, Result(Unit))
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