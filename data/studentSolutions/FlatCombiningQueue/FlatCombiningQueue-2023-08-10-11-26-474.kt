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
        while(true) {
            var taskCellIndex: Int? = null
            if (combinerLock.compareAndSet(false, true)) {
                queue.addLast(element)
                traverseTasks()
                return
            } else {
                if (taskCellIndex == null) {
                    taskCellIndex = randomCellIndex()
                    tasksForCombiner.compareAndSet(taskCellIndex, null, element)
                } else {
                    if (tasksForCombiner.compareAndSet(taskCellIndex, Result(element), null)) return
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
        while(true) {
            var taskCellIndex: Int? = null
            if (combinerLock.compareAndSet(false, true)) {
                val res = queue.removeFirstOrNull()
                // traverse tasks
                traverseTasks()
                return res
            } else {
                if (taskCellIndex == null) {
                    taskCellIndex = randomCellIndex()
                    tasksForCombiner.compareAndSet(taskCellIndex, null, Dequeue)
                } else {
                    val e = tasksForCombiner.get(taskCellIndex)
                    if (tasksForCombiner.compareAndSet(taskCellIndex, Result(e), null))
                        return e as E?
                }
            }
            return null
        }
    }

    fun traverseTasks() {
        for(i in 0 until tasksForCombiner.length()) {
            when(val task = tasksForCombiner.get(i)) {
                Dequeue -> dequeue()
                Result -> continue
                //enqueue operation
                else -> {
                    val e = task as E
                    queue.addLast(e) // or enqueue(e)
                    tasksForCombiner.compareAndSet(i, e, Result(e))
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