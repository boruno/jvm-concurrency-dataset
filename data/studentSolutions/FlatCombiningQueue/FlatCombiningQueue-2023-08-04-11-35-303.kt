//package day4

import Result
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
        var index: Int? = null
        while (true) {
            if (tryLock()) {
                queue.addLast(element)
                processOperations()
                releaseLock()
                return
            } else {
                if (index == null) {
                    val randomIndex = randomCellIndex()
                    if (tasksForCombiner[randomIndex].compareAndSet(null, element)) {
                        index = randomIndex
                    }
                } else {
                    if (tasksForCombiner[index].value == element) continue
                    return
                }
            }
        }

    }

    private fun tryLock(): Boolean {
        return combinerLock.compareAndSet(false, true)
    }

    private fun processOperations() {
        (0 until tasksForCombiner.size).forEach {
            val task = tasksForCombiner[it]
            when (task.value) {
                Dequeue -> {
                    // dequeue
                    task.compareAndSet(Dequeue, Result(queue.removeFirst()))
                }
                is Result<*> -> {
                    // have a result

                }
                else -> {
                    // enqueue
                    queue.add(task.value as E)
                    task.value =  null
                }
            }
        }
    }
    private fun releaseLock() {
        combinerLock.value = false
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
        var index: Int? = null
        while (true) {
            if (tryLock()) {
                val result = queue.removeFirstOrNull()
                processOperations()
                releaseLock()
                return result
            } else {
                if (index == null) {
                    val randomIndex = randomCellIndex()
                    if (tasksForCombiner[randomIndex].compareAndSet(null, Dequeue)) {
                        index = randomIndex
                    }
                } else {
                    if (tasksForCombiner[index].value == Dequeue) continue
                    val result = tasksForCombiner[index].value
                    if (result is Result<*>) {
                        return result.value as E
                    }
                    throw IllegalStateException("Wrong value in tasksForCombiner[$index]")
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