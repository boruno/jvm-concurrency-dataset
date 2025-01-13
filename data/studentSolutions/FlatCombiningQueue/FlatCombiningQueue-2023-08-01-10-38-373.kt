//package day2

import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    data class Element<E>(val value: E?, val push: Boolean)

    override fun enqueue(element: E) {
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
        applyTasks(index, pushed, element)
    }

    override fun dequeue(): E? {
        var index = randomCellIndex()
        var pushed = false
        while (!combinerLock.compareAndSet(false, true)) {
            if (pushed) {
                val element = tasksForCombiner[index].value
                if (element is Element<*>) {
                    tasksForCombiner[index].compareAndSet(element, null)
                    return element.value as E?
                }
            } else {
                if (tasksForCombiner[index].compareAndSet(null, DEQUE_TASK)) pushed = true
                else index = randomCellIndex()
            }
        }
        return applyTasks(index, pushed, null)
    }

    private fun applyTasks(index: Int, pushed: Boolean, element: E?): E? {
        var result: E? = null
        for (taskIndex in 0..(tasksForCombiner.size - 1)) {
            val localElement = tasksForCombiner[taskIndex].value ?: continue
            if (localElement is Element<*> && localElement.push) {
                queue.addLast(localElement.value as E)
                tasksForCombiner[taskIndex].compareAndSet(localElement, PROCESSED)
            }
            if (localElement == DEQUE_TASK) {
                val removedElement = queue.removeFirstOrNull()
                tasksForCombiner[taskIndex].compareAndSet(DEQUE_TASK, Element(removedElement, false))
                if (taskIndex == index) result = removedElement
            }
        }
        if (!pushed && element != null) queue.addLast(element)
        combinerLock.compareAndSet(true, false)
        return result
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
