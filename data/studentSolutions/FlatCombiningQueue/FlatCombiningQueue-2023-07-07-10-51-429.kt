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
        var i: Int? = null
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                queue.addLast(element)
                goThroughTasks()
                cleanCell(i)
                combinerLock.value = false
                return
            } else {
                if (i == null) {
                    i = randomCellIndex()
                    if (!tasksForCombiner[i].compareAndSet(null, element)) {
                        i = null
                    }
                } else {
                    if (tasksForCombiner[i].value is Result<*>) {
                        cleanCell(i)
                        return
                    }
                }
            }
        }
    }

    fun cleanCell(index: Int?) {
        index ?: return
        tasksForCombiner[index].value = null
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
        var i: Int? = null
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                val e = queue.removeFirstOrNull()
                goThroughTasks()
                cleanCell(i)
                combinerLock.value = false
                return e
            } else {
                if (i == null) {
                    i = randomCellIndex()
                    if (!tasksForCombiner[i].compareAndSet(null, Dequeue)) {
                        i = null
                    }
                } else {
                    val value = tasksForCombiner[i].value
                    if (value is Result<*>) {
                        cleanCell(i)
                        return value.value as E
                    }
                }
            }
        }
    }

    private fun goThroughTasks() {
        for (i in 0..tasksForCombiner.size) {
            val e = tasksForCombiner[i].value ?: continue
            when (e) {
                Dequeue -> {
                    tasksForCombiner[i].compareAndSet(e, Result(queue.removeFirstOrNull()))
                }
                is Result<*> -> {}
                else -> {
                    e as E
                    queue.addLast(e)
                    tasksForCombiner[i].compareAndSet(e, Result(Unit))
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

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)