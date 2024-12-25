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


        if (tryLock()) { // (1)
            enqueueWithLock(element) // (2a)
            return
        }
        waitUntilResultOrEnqueueWithLock(element) // (2b)
    }

    private fun waitUntilResultOrEnqueueWithLock(element: E) {
        val index = randomCellIndex()
        val throughCombiner = tasksForCombiner.compareAndSet(index, null, element)
        while (true) {
            if (throughCombiner) {
                if (extractEnqueueResult(index)) return
            }
            if (tryLock()) {
                if (throughCombiner) {
                    if (extractEnqueueResult(index)) return
                }
                enqueueWithLock(element)
                return
            }
        }
    }

    private fun extractEnqueueResult(index: Int): Boolean {
        val result = checkResult(index)
        if (result != null) {
            clean(index)
            return true
        }
        return false
    }

    private fun enqueueWithLock(element: E) {
        queue.addLast(element)
        helpToDoTasks()
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
        if (tryLock()) { // (1)
            return dequeueWithUnlock() // (2a)
        }
        return waitForResultOrDequeueWithLock() // (2b)
    }

    private fun waitForResultOrDequeueWithLock(): E? {
        val index = randomCellIndex()
        val success = tasksForCombiner.compareAndSet(index, null, Dequeue)
        while (true) {
            if (success) {
                extractDequeueResult(index)?.also { result: Result<*> ->
                    return result.value as E?
                }
            }
            if (tryLock()) {
                if (success) {
                    extractDequeueResult(index)?.also { result: Result<*> ->
                        return result.value as E?
                    }
                }
                return dequeueWithUnlock()
            }
        }
    }

    private fun extractDequeueResult(index: Int): Result<*>? {
        return checkResult(index)?.also { clean(index) }
    }

    private fun checkResult(index: Int): Result<*>? {
        return (tasksForCombiner.get(index) as? Result<*>)
    }

    private fun clean(index: Int) {
        tasksForCombiner.set(index, null)
    }

    private fun tryLock() = combinerLock.compareAndSet(false, true)
    private fun unlock() = combinerLock.set(false)

    private fun dequeueWithUnlock(): E? {
        val element = queue.removeFirstOrNull()
        helpToDoTasks()
        unlock()
        return element
    }

    private fun helpToDoTasks() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            tasksForCombiner.get(i)?.also { element ->
                when (element) {
                    is Dequeue -> {
                        val dequeueElement = queue.removeFirstOrNull()
                        tasksForCombiner.set(i, Result(dequeueElement))
                    }

                    !is Result<*> -> {
                        val enqueueElement = element as E
                        queue.addLast(enqueueElement)
                        tasksForCombiner.set(i, Result(enqueueElement))
                    }
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