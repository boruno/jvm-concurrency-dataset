package day4

import day1.Queue
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class FlatCombiningQueue<E : Any> : Queue<E> {
    private val queue = ArrayDeque<Any?>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun tryLock(): Boolean {
        return combinerLock.compareAndSet(expect = false, update = true)
    }

    private fun unlock() {
        check(combinerLock.compareAndSet(expect = true, update = false))
    }

    override fun enqueue(element: E) {
        if (tryLock()) {
            processCombinerQueue()
            queue.addLast(element)
            unlock()
            return
        }
        val cellIndex: Int = addToCombinerQueue(element)
        while (true) {
            val value = tasksForCombiner[cellIndex].value
            if (value is Result) {
                tasksForCombiner[cellIndex].compareAndSet(value, null)
                return // another thread helped
            }
            if (tryLock()) {
                processCombinerQueue()
                unlock()
            }
        }
    }

    override fun dequeue(): E? {
        if (tryLock()) {
            processCombinerQueue()
            val result = queue.removeFirstOrNull()
            unlock()
            return result as E?
        }
        val cellIndex = addToCombinerQueue(Dequeue)
        while (true) {
            val value = tasksForCombiner[cellIndex].value
            if (value is Result) {
                tasksForCombiner[cellIndex].compareAndSet(value, null)
                return value.value as E?
            }
            if (tryLock()) {
                processCombinerQueue()
                unlock()
            }
        }
    }

    private fun processCombinerQueue() {
        for (i in 0 until TASKS_FOR_COMBINER_SIZE) {
            val task = tasksForCombiner[i].value
            if (task === Dequeue) {
                val value = queue.removeFirstOrNull()
                tasksForCombiner[i].compareAndSet(task, Result(value))
            } else if (task != null) {
                queue.addLast(task)
                tasksForCombiner[i].compareAndSet(task, Result(Unit))
            }
        }
    }

    private fun addToCombinerQueue(element: Any): Int {
        while (true) {
            val cellIndex: Int = randomCellIndex()
            if (tasksForCombiner[cellIndex].compareAndSet(null, element)) {
                return cellIndex
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
private class Result(
    val value: Any?
)