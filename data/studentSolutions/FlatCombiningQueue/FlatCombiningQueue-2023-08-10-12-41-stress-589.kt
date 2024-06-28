package day4

import day1.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        enqueueOperation(element)
    }

    override fun dequeue(): E? = enqueueOperation(Dequeue)
        ?.let { result -> (result as? Result<*>)?.value as? E }

    private fun enqueueOperation(operation: Any?): Any? {
        // This code thread-safe using the flat-combining technique.

        // 1.  Try to become a combiner by
        //     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        if (combinerLock.compareAndSet(false, true)) {
            val result = applyOperation(operation)
            combinerLock.set(false)
            return result
        } else {
            // 2b. If the lock is already acquired, announce this operation in
            //     `tasksForCombiner` by replacing a random cell state from
            //      `null` with the element.
            var settledIndex = randomCellIndex()
            while (!tasksForCombiner.compareAndSet(settledIndex, null, operation)) {
                settledIndex = randomCellIndex()
            }

            while (true) {
                val cellState = tasksForCombiner.get(settledIndex)
                when {
                    // Wait until either the cell state updates to `Result` (do not forget to clean it in this case),
                    cellState is Result<*> -> {
                        // enqueue successful
                        tasksForCombiner.set(settledIndex, null)
                        return cellState.value
                    }
                    // or `combinerLock` becomes available to acquire.
                    combinerLock.compareAndSet(false, true) -> {
                        val probablyResult = tasksForCombiner.getAndSet(settledIndex, null)
                        val result = if (probablyResult is Result<*>) {
                            probablyResult
                        } else {
                            applyOperation(operation)
                        }

                        combinerLock.set(false)
                        return result
                    }
                }
            }
        }
    }

    private fun applyOperation(operation: Any?): Result<E?> {
        val result = executeOperation(operation)

        helpOthers()

        return result
    }

    private fun helpOthers() {
        // TODO: 2a. On success, apply this operation and help others by traversing
        //     `tasksForCombiner`, performing the announced operations, and
        //      updating the corresponding cells to `Result`.
        (0 until TASKS_FOR_COMBINER_SIZE).forEach { actionIndex ->
            when (val action = tasksForCombiner.get(actionIndex)) {
                null -> return@forEach
                is Result<*> -> return@forEach
                else -> { // is Dequeue or element
                    tasksForCombiner.set(actionIndex, executeOperation(action))
                }
            }
        }
    }

    private fun executeOperation(operation: Any?): Result<E?> =
        when (operation) {
            is Dequeue -> Result(queue.removeFirstOrNull())
            else -> {
                queue.addLast(operation as E)
                Result(null)
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
