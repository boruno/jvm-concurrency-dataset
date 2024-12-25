//package day4

import day1.*
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
        if (tryLock()) {
            queue.addLast(element)
            doCombinerJob()
            return
        }
        val randomIndex = randomCellIndex()
        val success = tasksForCombiner.compareAndSet(randomIndex, null, element)
        while (true) {
            if (success) {
                val result = tasksForCombiner[randomIndex]
                if (result is Result<*>) return
            }
            if (tryLock()) {
                queue.addLast(element)
                doCombinerJob()
                return
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
            val element = queue.removeFirstOrNull()
            doCombinerJob()
            return element
        }
        val randomIndex = randomCellIndex()
        val success = tasksForCombiner.compareAndSet(randomIndex, null, Dequeue)
        while (true) {
            if (success) {
                val result = tasksForCombiner[randomIndex]
                if (result is Result<*>) return result.value as E
            }
            if (tryLock()) {
                val element = queue.removeFirstOrNull()
                doCombinerJob()
                return element
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.length())


    private fun tryLock() = combinerLock.compareAndSet(false, true)

    private fun doCombinerJob() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[i]
            if (task == Dequeue) {
                val element = queue.removeFirstOrNull()
                tasksForCombiner[i] = Result(element)
            } else if (task != null) {
                queue.addLast(task as E)
                tasksForCombiner[i] = Result(null)
            }
        }
    }

}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)