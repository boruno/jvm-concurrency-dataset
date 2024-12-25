//package day4

import day1.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun tryLock(): Boolean = combinerLock.compareAndSet(false, true)
    private fun unlock() = combinerLock.set(false)

    private fun help() {
        for (i in 0 until tasksForCombiner.length()) {
            when (tasksForCombiner.get(i)) {
                null -> continue
                is Result<*> -> continue
                Dequeue -> {
                    val res = queue.removeFirstOrNull()
                    tasksForCombiner.set(i, Result(res))
                }
                else -> {
                    val element = tasksForCombiner.get(i) as E
                    queue.addLast(element)
                    tasksForCombiner.set(i, Result(element))
                }
            }
        }
    }

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
            queue.addLast(element)
            help()
            unlock()
        } else {
            var randomCell = randomCellIndex()
            while (true) {
                if (tasksForCombiner.compareAndSet(randomCell, null, element)) break
                randomCell = randomCellIndex()
            }
            while (true) {
                val cellState = tasksForCombiner.get(randomCell)
                if (cellState is Result<*>) {
                    tasksForCombiner.set(randomCell, null)
                    return
                }
                if (tryLock()) {
                    help()
                    unlock()
                    tasksForCombiner.set(randomCell, null)
                    return
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
            val res = queue.removeFirstOrNull()
            help()
            unlock()
            return res
        } else {
            var randomCell = randomCellIndex()
            while (true) {
                if (tasksForCombiner.compareAndSet(randomCell, null, Dequeue)) break
                randomCell = randomCellIndex()
            }
            while (true) {
                val cellState = tasksForCombiner.get(randomCell)
                if (cellState is Result<*>) {
                    tasksForCombiner.set(randomCell, null)
                    return cellState.value as E?
                }
                if (tryLock()) {
                    val res = queue.removeFirstOrNull()
                    tasksForCombiner.set(randomCell, null)
                    help()
                    unlock()
                    return res
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