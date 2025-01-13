//package day4

import Queue
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
        var cellIndex = randomCellIndex()
        var gotCell = false
        while (true) {
            if (tryLock()) {
                val cell = tasksForCombiner.get(cellIndex)
                // We had asked for help, but got the lock first
                if(gotCell) {
                    if (cell == null) {

                    } else if (cell is Result<*>) {
                        if (tasksForCombiner.compareAndSet(cellIndex, cell as E, null)) {
                            queue.addLast(cell as E)
                        }
                    } else {
                        if (tasksForCombiner.compareAndSet(cellIndex, cell, null))
                            queue.addLast(element)
                    }
                } else {
                    tasksForCombiner.compareAndSet(cellIndex, element, null)
                    queue.addLast(element)
                }
                helpArray()
                unlock()
                return

            } else {
                if (tasksForCombiner.compareAndSet(cellIndex, null, element)) {
                    gotCell = true
                } else if(!gotCell) {
                    cellIndex = randomCellIndex()
                }
                if (gotCell) {
                    val cellValue = tasksForCombiner.get(cellIndex)
                    if (cellValue is Result<*>) {
                        tasksForCombiner.compareAndSet(cellIndex, cellValue, null)
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
        var cellIndex = randomCellIndex()
        var gotCell = false
        while (true) {
            if (tryLock()) {
                val rez: E?
                val cell = tasksForCombiner.get(cellIndex)
                if (gotCell && cell != null && cell is Result<*>) {
                    tasksForCombiner.compareAndSet(cellIndex, cell, null)
                    rez = cell.value as E
                } else {
                    tasksForCombiner.compareAndSet(cellIndex, Dequeue, null)
                    rez = queue.removeFirstOrNull()
                }
                helpArray()
                unlock()
                return rez
            } else {
                if (tasksForCombiner.compareAndSet(cellIndex, null, Dequeue)) {
                    gotCell = true
                } else if(!gotCell) {
                    cellIndex = randomCellIndex()
                }
                if (gotCell) {
                    val cellValue = tasksForCombiner.get(cellIndex)
                    if (cellValue is Result<*>) {
                        tasksForCombiner.compareAndSet(cellIndex, Dequeue, null)
                        return cellValue.value as E
                    }
                }

            }
        }
    }

    private fun tryLock(): Boolean {
        return combinerLock.compareAndSet(false, true)
    }

    private fun unlock() {
        combinerLock.set(false)
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.length())

    private fun helpArray() {
        for (index in 0 until tasksForCombiner.length()) {
            when (val cell = tasksForCombiner.get(index)) {
                // dequeue
                is Dequeue -> {
                    val result = queue.removeFirstOrNull()
                    tasksForCombiner.compareAndSet(index, Dequeue, Result(result))
                }
                // already processed, leave for another thread to pick
                is Result<*> -> { }
                null -> {}
                // enqueue request, put result to the queue
                else -> {
                    if (tasksForCombiner.compareAndSet(index, cell as E, Result(cell))) {
                        queue.addLast(cell as E)
                    }
                }
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