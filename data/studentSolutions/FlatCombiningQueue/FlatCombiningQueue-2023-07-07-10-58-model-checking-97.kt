package day4

import day1.*
import kotlinx.atomicfu.*
import java.util.concurrent.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = atomic(false) // unlocked initially
    private val tasksForCombiner = atomicArrayOfNulls<Op>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        val op = Enqueue(element)
        while (true) {
            if (tryLock()) {
                queue.addLast(element)
                assist()
                unlock()
                return
            }
            val index = randomCellIndex()
            if (!tasksForCombiner[index].compareAndSet(null, op)) {
                continue
            }
            while (true) {
                if (tasksForCombiner[index].value is EnqueueResult) {
                    tasksForCombiner[index].value = null
                    return
                }
                if (tryLock()) {
                    if (tasksForCombiner[index].compareAndSet(op, null)) {
                        queue.addLast(element)
                        assist()
                        unlock()
                        return
                    }
                    require(tasksForCombiner[index].value is EnqueueResult) {
                        "Unexpected op at $index: ${tasksForCombiner[index].value}"
                    }
                    tasksForCombiner[index].value = null
                    return
                }
            }
        }
    }

    override fun dequeue(): E? {
        val op = Dequeue
        while (true) {
            if (tryLock()) {
                val result = queue.removeFirstOrNull()
                assist()
                unlock()
                return result
            }
            val index = randomCellIndex()
            if (!tasksForCombiner[index].compareAndSet(null, op)) {
                continue
            }
            while (true) {
                val v = tasksForCombiner[index].value
                if (v is DequeueResult<*>) {
                    tasksForCombiner[index].value = null
                    @Suppress("UNCHECKED_CAST")
                    return v.value as E?
                }
                if (tryLock()) {
                    if (tasksForCombiner[index].compareAndSet(op, null)) {
                        val result = queue.removeFirstOrNull()
                        assist()
                        unlock()
                        return result
                    }
                    val v = tasksForCombiner[index].value
                    require(v is DequeueResult<*>) {
                        "Unexpected op at $index: $v"
                    }
                    tasksForCombiner[index].value = null
                    @Suppress("UNCHECKED_CAST")
                    return v.value as E?
                }
            }
        }
    }

    private fun assist() {
        for (i in 0 until tasksForCombiner.size) {
            when (val v = tasksForCombiner[i].value) {
                is Dequeue -> {
                    val result = queue.removeFirstOrNull()
                    tasksForCombiner[i].value = DequeueResult(result)
                    continue
                }

                is Enqueue<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    queue.addLast(v.value as E)
                    tasksForCombiner[i].value = EnqueueResult
                    continue
                }
            }
        }
    }

    private fun tryLock(): Boolean {
        return combinerLock.compareAndSet(false, true)
    }

    private fun unlock() {
        combinerLock.value = false
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.size)
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

private interface Op

private object Dequeue : Op

private class DequeueResult<V>(
    val value: V
) : Op

private class Enqueue<V>(
    val value: V
) : Op

private object EnqueueResult : Op