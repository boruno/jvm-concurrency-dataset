@file:Suppress("FoldInitializerAndIfToElvis")

package day4

import day1.Queue
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun tryLock(): Boolean {
        return combinerLock.compareAndSet(false, true)
    }

    private fun unlock() {
        combinerLock.value = false
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

        var queuedIndex = -1
        while (true) {
            if (!tryLock()) {
                if (queuedIndex == -1) {
                    val index = randomCellIndex()
                    if (tasksForCombiner[index].compareAndSet(null, element)) {
                        queuedIndex = index
                    }
                } else {
                    val currentCell = tasksForCombiner[queuedIndex].value
                    if (currentCell is Result<*>) {
                        tasksForCombiner[queuedIndex].value = null
                        return
                    }
                }
                continue
            }

            val needProcessing = if (queuedIndex != -1) {
                val currentCell = tasksForCombiner[queuedIndex].value
                tasksForCombiner[queuedIndex].value = null
                currentCell !is Result<*>
            } else {
                true
            }

            if (needProcessing) {
                queue.addLast(element)
            }

            processCombinerQueue()
            unlock()
            return
        }
    }

    private fun processCombinerQueue() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val cell = tasksForCombiner[i].value
            if (cell == null || cell is Result<*>) {
                continue
            }

            if (cell == Dequeue) {
                val dequeued = queue.removeFirstOrNull()
                tasksForCombiner[i].value = Result(dequeued)
            }

            queue.addLast(cell as E)
            tasksForCombiner[i].value = Result(Unit)
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

        var queuedIndex = -1
        while (true) {
            if (!tryLock()) {
                if (queuedIndex == -1) {
                    val index = randomCellIndex()
                    if (tasksForCombiner[index].compareAndSet(null, Dequeue)) {
                        queuedIndex = index
                    }
                } else {
                    val currentCell = tasksForCombiner[queuedIndex].value
                    if (currentCell is Result<*>) {
                        tasksForCombiner[queuedIndex].value = null
                        return currentCell.value as E
                    }
                }
                continue
            }

            var needProcessing = true
            var result: E? = null

            if (queuedIndex != -1) {
                val currentCell = tasksForCombiner[queuedIndex].value
                tasksForCombiner[queuedIndex].value = null
                if (currentCell is Result<*>) {
                    needProcessing = false
                    result = currentCell.value as E
                }
            }

            if (needProcessing) {
                result = queue.removeFirstOrNull()
            }

            processCombinerQueue()
            unlock()
            return result
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