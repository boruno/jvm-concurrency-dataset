package day4

import day1.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    fun tryLock() = combinerLock.compareAndSet(false, true)

    fun unlock() = combinerLock.set(false)

    fun combiner() {
        for (i in 0 until tasksForCombiner.length()) {
            val task = tasksForCombiner.get(i)
            if (task is Result<*>) {
                continue
            } else if (task === Dequeue) {
                tasksForCombiner.compareAndSet(i, task, Result(queue.removeFirstOrNull()))
            } else if (task != null) {
                tasksForCombiner.compareAndSet(i, task, Result(queue.addLast(task as E)))
            } else {
                // empty
            }
        }
        unlock()
    }

    override fun enqueue(element: E) {
        if (tryLock()) {
            queue.addLast(element)
            // I am a combiner
            combiner()
        } else {
            var idx: Int
            while (true) {
                idx = randomCellIndex()
                if (tasksForCombiner.compareAndSet(idx, null, element)) {
                    break
                }
            }
            while (true) {
                val value = tasksForCombiner.get(idx)
                if (value === element) {
                    if (tryLock()) {
                        queue.addLast(element)
                        // I am the combiner
                        combiner()
                        return
                    }
                } else if (value is Result<*>) {
                    // we have a result here
                    // remove it and exit
                    tasksForCombiner.compareAndSet(idx, value, null)
                    return
                }
            }
        }
    }

    override fun dequeue(): E? {
        if (tryLock()) {
            val res = queue.removeFirstOrNull()
            // I am a combiner
            combiner()
            return res
        } else {
            var idx: Int
            while (true) {
                idx = randomCellIndex()
                if (tasksForCombiner.compareAndSet(idx, null, Dequeue)) {
                    break
                }
            }
            while (true) {
                val value = tasksForCombiner.get(idx)
                if (value === Dequeue) {
                    if (tryLock()) {
                        val res = queue.removeFirstOrNull()
                        // I am the combiner
                        combiner()
                        return res
                    }
                } else if (value is Result<*>) {
                    // we have a result here
                    // remove it and exit
                    tasksForCombiner.compareAndSet(idx, value, null)
                    return value.value as E?
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