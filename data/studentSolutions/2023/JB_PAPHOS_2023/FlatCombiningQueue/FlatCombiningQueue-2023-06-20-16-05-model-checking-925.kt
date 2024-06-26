package day2

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    data class Element<E>(val value: E?, val push: Boolean)

    override fun enqueue(element: E) {
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // TODO: 2a. On success, apply this operation and help others by traversing
        // TODO:     `tasksForCombiner`, performing the announced operations, and
        // TODO:      updating the corresponding cells to `PROCESSED`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` to the element. Wait until either the cell state
        // TODO:      updates to `PROCESSED` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        var index = randomCellIndex()
        var pushed = false
        while (!combinerLock.compareAndSet(false, true)) {
            if (pushed) {
                if (tasksForCombiner[index].compareAndSet(PROCESSED, null)) return
            } else {
                if (tasksForCombiner[index].compareAndSet(null, Element(element, true))) pushed = true
                else index = randomCellIndex()
            }
        }
        for (taskIndex in 0 until tasksForCombiner.size) {
            val localElement = tasksForCombiner[taskIndex].value ?: continue
            if (localElement is Element<*> && localElement.push) {
                @Suppress("UNCHECKED_CAST")
                queue.addLast(localElement.value as E)
                tasksForCombiner[taskIndex].compareAndSet(localElement, PROCESSED)
            }
            if (localElement == DEQUE_TASK) {
                tasksForCombiner[taskIndex].compareAndSet(DEQUE_TASK, Element(queue.removeFirstOrNull(), false))
            }
        }
        if (!pushed) queue.addLast(element)
        combinerLock.compareAndSet(true, false)
    }

    override fun dequeue(): E? {
        // TODO: Make this code thread-safe using the flat-combining technique.
        // TODO: 1.  Try to become a combiner by
        // TODO:     changing `combinerLock` from `false` (unlocked) to `true` (locked).
        // TODO: 2a. On success, apply this operation and help others by traversing
        // TODO:     `tasksForCombiner`, performing the announced operations, and
        // TODO:      updating the corresponding cells to `PROCESSED`.
        // TODO: 2b. If the lock is already acquired, announce this operation in
        // TODO:     `tasksForCombiner` by replacing a random cell state from
        // TODO:      `null` to `DEQUE_TASK`. Wait until either the cell state
        // TODO:      updates to `PROCESSED` (do not forget to clean it in this case),
        // TODO:      or `combinerLock` becomes available to acquire.
        var index = randomCellIndex()
        var pushed = false
        while (!combinerLock.compareAndSet(false, true)) {
            if (pushed) {
                val element = tasksForCombiner[index].value
                if (element is Element<*>) {
                    tasksForCombiner[index].compareAndSet(element, null)
                    @Suppress("UNCHECKED_CAST")
                    return element.value as E?
                }
            } else {
                if (tasksForCombiner[index].compareAndSet(null, DEQUE_TASK)) pushed = true
                else index = randomCellIndex()
            }
        }
        for (taskIndex in 0 until tasksForCombiner.size) {
            val localElement = tasksForCombiner[taskIndex].value ?: continue
            if (localElement is Element<*> && localElement.push) {
                @Suppress("UNCHECKED_CAST")
                queue.addLast(localElement.value as E)
                tasksForCombiner[taskIndex].compareAndSet(localElement, PROCESSED)
            }
            if (localElement == DEQUE_TASK) {
                tasksForCombiner[taskIndex].compareAndSet(DEQUE_TASK, Element(queue.removeFirstOrNull(), false))
            }
        }
        val element: E?
        if (!pushed) {
            element = queue.removeFirstOrNull()
        } else {
            @Suppress("UNCHECKED_CAST")
            val node = tasksForCombiner[index].value as Element<E>
            element = node.value
            tasksForCombiner[index].compareAndSet(node, null)
        }
        combinerLock.compareAndSet(true, false)
        return element
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private val DEQUE_TASK = Any()

// TODO: Put this token in `tasksForCombiner` when the task is processed.
private val PROCESSED = Any()