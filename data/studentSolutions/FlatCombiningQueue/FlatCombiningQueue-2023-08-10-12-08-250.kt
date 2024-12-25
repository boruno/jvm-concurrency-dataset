//package day4

import day1.Queue
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
        if (tryLock()) {
            addAndCombine {
                queue.addLast(element)
            }
        } else {
            val index = installToRandomCell(element)
            while (true) {
                if (tasksForCombiner[index] != element) {
                    tasksForCombiner[index] = null
                    break
                }
                if (tryLock()) {
                    tasksForCombiner[index] = null
                    addAndCombine {
                        queue.addLast(element)
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
        if (tryLock()) {
            return addAndCombine {
                queue.removeFirstOrNull()
            } as E?
        } else {
            val index = installToRandomCell(Dequeue)
            while (true) {
                if (tasksForCombiner[index] != Dequeue) {
                    val value = (tasksForCombiner[index] as Result<*>).value as E?
                    tasksForCombiner[index] = null
                    return value
                }
                if (tryLock()) {
                    return addAndCombine {
                        tasksForCombiner[index] = null
                        queue.removeFirstOrNull()
                    } as E?
                }
            }
        }
    }

    private fun installToRandomCell(obj: Any?): Int {
        var index: Int
        do {
            index = randomCellIndex()
        } while (!tasksForCombiner.compareAndSet(index, null, obj))
        return index
    }

    private fun tryLock() = combinerLock.compareAndSet(false, true)
    private fun unlock() = combinerLock.set(false)

    private fun addAndCombine(block: () -> Any?): Any? {
        val answer: Any?
        try {
            answer = block()
            repeat(TASKS_FOR_COMBINER_SIZE) { idx ->
                when (val cellValue = tasksForCombiner[idx]) {
                    is Dequeue -> tasksForCombiner[idx] = Result(queue.removeFirstOrNull())
                    null, is Result<*> -> {} // do nothing
                    else -> {
                        queue.addLast(cellValue as E)
                        tasksForCombiner[idx] = Result(null)
                    }
                }
            }
            return answer
        } finally {
            unlock()
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