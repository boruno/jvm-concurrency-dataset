package day4

import day1.*
import day4.Result
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.random.Random

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
        var i = randomCellIndex()
        var isPlaced = false
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                queue.addLast(element)
                if (isPlaced) {
                    tasksForCombiner.set(i, null)
                }
                for (i in 0..TASKS_FOR_COMBINER_SIZE - 1) {
                    val cell = tasksForCombiner.get(i)
                    if (cell == null) {
                        continue
                    }
                    if (cell is Dequeue) {
                        tasksForCombiner.compareAndSet(i, Dequeue, Result(queue.removeFirstOrNull()))
                    } else if (cell !is Result<*>) {
                        val result: E = tasksForCombiner.get(i) as E
                        queue.addLast(result)
                        tasksForCombiner.compareAndSet(i, Result(result), null)
                    }
                }
                combinerLock.compareAndSet(true, false)
                return
            } else {
                // non-combiner
                if (!isPlaced) {
                    while (!tasksForCombiner.compareAndSet(i, null, element)) {
                        i = randomCellIndex()
                    }
                    isPlaced = true
                } else {
                    if (tasksForCombiner.get(i) is Result<*>) {
                        tasksForCombiner.compareAndSet(i, Result(element), null)
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
        var i = randomCellIndex()
        var isPlaced = false
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                // combiner
                val result: E?
                if (isPlaced && !tasksForCombiner.compareAndSet(i, Dequeue, null)) {
                    result = tasksForCombiner.getAndSet(i, null) as E?
                } else {
                    result = queue.removeFirstOrNull()
                }
                for (i in 0..TASKS_FOR_COMBINER_SIZE - 1) {
                    val cell = tasksForCombiner.get(i)
                    if (cell == null) {
                        continue
                    }
                    if (cell is Dequeue) {
                        tasksForCombiner.compareAndSet(i, Dequeue, Result(queue.removeFirstOrNull()))
                    } else if (cell !is Result<*>) {
                        val result: E = tasksForCombiner.get(i) as E
                        queue.addLast(result)
                        tasksForCombiner.compareAndSet(i, Result(result), null)
                    }
                }
                combinerLock.compareAndSet(true, false)
                return result
            } else {
                // non-combiner
                if (!isPlaced) {
                    while (!tasksForCombiner.compareAndSet(i, null, Dequeue)) {
                        i = randomCellIndex()
                    }
                    isPlaced = true
                } else {
                    if (tasksForCombiner.get(i) is Result<*>) {
                        return (tasksForCombiner.getAndSet(i, null) as Result<*>).value as E?
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