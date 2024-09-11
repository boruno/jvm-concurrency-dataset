package day4

import day1.*
import day4.Result
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E : Any> : Queue<E> {
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
            enqueueWithLock(element)
        } else {
            val index = tryInstall(element)
            while (true) {
                val result = tasksForCombiner.get(index)
                when {
                    result is Result<*> -> {
                        if (tasksForCombiner.compareAndSet(index, result, null)) return
                    }
                    tryLock() -> {
                        helpOthers()
                        tasksForCombiner.set(index, null) // clearing
                        unlock()
                        return
                    }
                }
            }
        }
    }

    private fun tryInstall(element: Any): Int {
        while (true) {
            val index = randomCellIndex()
            if (tasksForCombiner.compareAndSet(index, null, element)) return index
        }
    }

    private fun enqueueWithLock(element: E) {
        queue.addLast(element)
        helpOthers()
        unlock()
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
            helpOthers()
            val result = queue.removeFirstOrNull()
            unlock()
            return result
        } else {
            val index = tryInstall(Dequeue)
            while (true) {
                val result = tasksForCombiner.get(index)
                when {
                    result is Result<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        if (tasksForCombiner.compareAndSet(index, result, null)) return result.value as? E
                    }
                    tryLock() -> {
                        val existingResult = tasksForCombiner.get(index)
                        tasksForCombiner.set(index, null) // clearing
                        helpOthers()
                        val finalResult = if (existingResult is Result<*>) existingResult.value else queue.removeFirstOrNull()
                        unlock()
                        @Suppress("UNCHECKED_CAST")
                        return finalResult as? E
                    }
                }
            }
        }
    }

    fun tryLock(): Boolean = combinerLock.compareAndSet(false, true)
    fun unlock() { combinerLock.set(false) }

    private fun helpOthers() {
        for (i in 0 until tasksForCombiner.length()) {
            val value = tasksForCombiner.get(i)
            @Suppress("UNCHECKED_CAST")
            when (value) {
                null -> continue
                is Dequeue -> {
                    val deqValue = queue.removeFirstOrNull()
                    tasksForCombiner.compareAndSet(i, value, Result(deqValue))
                }
                else -> {
                    if (tasksForCombiner.compareAndSet(i, value, Result(value)))
                        queue.addLast(value as E)
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