//package day4

import day1.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class SpinLock {
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    fun withLock(operation: () -> Any?): Any? {
        // This code thread-safe using the flat-combining technique.

        // 1.  Try to become a combiner by
        //     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        if (combinerLock.compareAndSet(false, true)) {
            val result = executeOperationAndHelp(operation)
            combinerLock.set(false)
            return result.value
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
                            executeOperationAndHelp(operation)
                        }

                        combinerLock.set(false)
                        return result.value
                    }
                }
            }
        }
    }

    private fun executeOperationAndHelp(operation: () -> Any?): Result<Any?> {
        val result = executeOperation(operation)

        helpOthers()

        return result
    }

    private fun helpOthers() {
        // 2a. On success, apply this operation and help others by traversing
        //    `tasksForCombiner`, performing the announced operations, and
        //     updating the corresponding cells to `Result`.

        (0 until TASKS_FOR_COMBINER_SIZE)
            .asSequence()
            .map { index -> index to tasksForCombiner.get(index) }
            .filter { (_, action) -> action != null && action !is Result<*> }
            .forEach { (actionIndex, action) ->
                tasksForCombiner.set(actionIndex, executeOperation(action as (() -> Any?)))
            }
    }

    private fun executeOperation(operation: () -> Any?): Result<Any?> = Result(operation())

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.length())
}

class FlatCombiningQueue<E> : Queue<E> {
    private val spinLock = SpinLock()
    private val queue = ArrayDeque<E>() // sequential queue

    override fun enqueue(element: E) {
        spinLock.withLock {
            queue.addLast(element)
            null
        }
    }

    override fun dequeue(): E? = spinLock.withLock { queue.firstOrNull() } as? E
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)
