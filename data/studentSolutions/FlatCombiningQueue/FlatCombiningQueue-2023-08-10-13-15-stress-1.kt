package day4

import day1.*
import day4.Result
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    private fun tryLock() : Boolean {
        return combinerLock.compareAndSet(false, true)
    }

    private fun unlock() {
        combinerLock.set(false)
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
        while(true){
            if(tryLock()) {
                queue.addLast(element)
                helpOthers()
                unlock()
                return
            }
            else{
                val randomCellIndex = randomCellIndex()
                if(tasksForCombiner.compareAndSet(randomCellIndex, null, element))
                {
                    while (true)
                    {
                        val get = tasksForCombiner.get(randomCellIndex)
                        if(get is Result<*>)
                        {
                            if(tasksForCombiner.compareAndSet(randomCellIndex, get, null))
                                return
                        }
                        else if(!combinerLock.get())
                        {
                            tasksForCombiner.compareAndSet(randomCellIndex, element, null)
                            break
                        }
                    }
                }
            }
        }
    }

    private fun helpOthers() {
        for (index in 0 until tasksForCombiner.length()) {
            when (val cellValue = tasksForCombiner.get(index)) {
                is Dequeue -> tasksForCombiner.set(index, Result(queue.removeFirstOrNull()))
                is Result<*> -> {}
                null -> {}
                else -> {
                    if (tasksForCombiner.compareAndSet(index, cellValue, Result(cellValue)))
                        queue.addLast(cellValue as E)
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


        while(true) {
            if (tryLock()) {

                val deqResult = queue.removeFirstOrNull()
                helpOthers()
                unlock()
                return deqResult
            } else {
                val randomCellIndex = randomCellIndex()

                if (tasksForCombiner.compareAndSet(randomCellIndex, null, Dequeue)) {
                    while (true) {
                        val get = tasksForCombiner.get(randomCellIndex)
                        if (get is Result<*>) {
                            if (tasksForCombiner.compareAndSet(randomCellIndex, get, null))
                                return get.value as E?
                        } else if (tryLock())
                        {
                            tasksForCombiner.compareAndSet(randomCellIndex, Dequeue, null)
                            val deqResult = queue.removeFirstOrNull()
                            helpOthers()
                            unlock()
                            return deqResult
                        }


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