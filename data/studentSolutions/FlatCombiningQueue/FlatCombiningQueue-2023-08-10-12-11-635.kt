//package day4

import day1.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        // This code thread-safe using the flat-combining technique.
        // 1.  Try to become a combiner by
        //     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        if (combinerLock.compareAndSet(false, true)) {
            // 2a. On success, apply this operation
            queue.addLast(element)

            helpOthers()

            combinerLock.set(false)
        } else {
            // 2b. If the lock is already acquired, announce this operation in
            //     `tasksForCombiner` by replacing a random cell state from
            //      `null` with the element.
            var settledIndex = randomCellIndex()
            while (!tasksForCombiner.compareAndSet(settledIndex, null, element)) {
                settledIndex = randomCellIndex()
            }

            while (true) {
                val cellState = tasksForCombiner.get(settledIndex)

                when {
                    // Wait until either the cell state updates to `Result` (do not forget to clean it in this case),
                    cellState is Result<*> -> {
                        // enqueue successful
                        tasksForCombiner.set(settledIndex, null)
                    }
                    // or `combinerLock` becomes available to acquire.
                    combinerLock.compareAndSet(false, true) -> {
                        tasksForCombiner.set(settledIndex, null) // unsettle task
                        queue.addLast(element)
                        helpOthers()
                        combinerLock.set(false)
                    }
                }
            }
        }
    }

    override fun dequeue(): E? {
        // This code thread-safe using the flat-combining technique.
        // 1.  Try to become a combiner by
        //     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        val result = if (combinerLock.compareAndSet(false, true)) {
            // 2a. On success, apply this operation
            val operationResult = queue.removeFirstOrNull()

            helpOthers()

            combinerLock.set(false)

            operationResult
        } else {
            // 2b. If the lock is already acquired, announce this operation in
            //     `tasksForCombiner` by replacing a random cell state from
            //      `null` with `Dequeue`.
            var settledIndex = randomCellIndex()
            while (!tasksForCombiner.compareAndSet(settledIndex, null, Dequeue)) {
                settledIndex = randomCellIndex()
            }

            var operationResult: E?
            while (true) {
                val cellState = tasksForCombiner.get(settledIndex)
                when {
                    // Wait until either the cell state updates to `Result` (do not forget to clean it in this case),
                    cellState is Result<*> -> {
                        // dequeue successful
                        tasksForCombiner.set(settledIndex, null)
                        operationResult = cellState.value as? E
                        break
                    }
                    // or `combinerLock` becomes available to acquire.
                    combinerLock.compareAndSet(false, true) -> {
                        tasksForCombiner.set(settledIndex, null)
                        operationResult = queue.removeFirstOrNull()

                        helpOthers()
                        combinerLock.set(false)
                        break
                    }
                }
            }

            operationResult
        }
        return result
    }

    private fun helpOthers() {
        // ...and help others by traversing
        //     `tasksForCombiner`, performing the announced operations, and
        //      updating the corresponding cells to `Result`.
        (0 until TASKS_FOR_COMBINER_SIZE).forEach { actionIndex ->
            val action = tasksForCombiner.get(actionIndex)

            when (action) {
                null -> return@forEach
                is Result<*> -> return@forEach
                is Dequeue -> {
                    val result = queue.removeFirstOrNull()
                    tasksForCombiner.set(actionIndex, Result(result))
                }
                else -> { // element
                    queue.addLast(action as E)
                    tasksForCombiner.set(actionIndex, Result(null))
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