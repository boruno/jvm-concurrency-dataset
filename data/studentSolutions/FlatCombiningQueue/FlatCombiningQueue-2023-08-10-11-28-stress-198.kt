package day4

import day1.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E: Any> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)
    
    private inline fun onSuccessLock(body: () -> Unit) {
        if (combinerLock.compareAndSet(false, true)) {
            try {
                body()
            } finally {
                combinerLock.set(false)
            }
        }
    }

    override tailrec fun enqueue(element: E) {
        onSuccessLock {
            queue.addLast(element)
            combine()
            return
        }
        val index = randomCellIndex()
        if (!tasksForCombiner.compareAndSet(index, null, element)) return enqueue(element)
        while (true) {
            val arrayValue = tasksForCombiner[index]
            if (arrayValue is Result<*>) {
                tasksForCombiner[index] = null
                return
            }
            onSuccessLock {
                combine()
                tasksForCombiner[index] = null
                return
            }
        }
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
    }

    private fun combine() {
        for (taskIndex in 0 until tasksForCombiner.length()) {
            @Suppress("UNCHECKED_CAST")
            when (val task = tasksForCombiner[taskIndex]) {
                is Result<*> -> Unit
                is Dequeue -> tasksForCombiner[taskIndex] = Result(queue.removeFirstOrNull())
                else -> tasksForCombiner[taskIndex] = Result(queue.addLast(task as E))
            }
        }
    }

    override tailrec fun dequeue(): E? {
        onSuccessLock {
            val result = queue.removeFirstOrNull()
            combine()
            return result
        }
        val index = randomCellIndex()
        if (!tasksForCombiner.compareAndSet(index, null, Dequeue)) return dequeue()
        @Suppress("UNCHECKED_CAST")
        while (true) {
            val arrayValue = tasksForCombiner[index]
            if (arrayValue is Result<*>) {
                tasksForCombiner[index] = null
                return arrayValue.value as E?
            }
            onSuccessLock {
                combine()
                val result = tasksForCombiner[index] as Result<*>
                tasksForCombiner[index] = null
                return result.value as E?
            }
        }
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