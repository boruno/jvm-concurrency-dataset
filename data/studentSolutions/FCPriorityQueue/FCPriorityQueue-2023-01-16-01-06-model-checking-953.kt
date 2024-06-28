import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.getAndUpdate
import java.util.*
import kotlin.random.Random

class FCPriorityQueue<E : Comparable<E>> {
    private sealed interface Action<E> {
        fun run(): E?
    }
    private inner class ENQ(val x: E): Action<E> {
        override fun run(): E? {
            q.add(x)
            return null
        }

        override fun toString(): String = "ENQ($x)"
    }
    private inner class DEQ(): Action<E> {
        override fun run(): E? {
            return q.poll()
        }

        override fun toString(): String = "DEQ()"
    }
    private inner class PEEK(): Action<E> {
        override fun run(): E? {
            return q.peek()
        }

        override fun toString(): String = "PEEK()"
    }
    private inner class Result(val x: E?): Action<E> {
        override fun run(): E? {
            return x
        }

        override fun toString(): String = "Result($x)"
    }

    private val q = PriorityQueue<E>()

    private val arraySize = 4
    private val fcArray = atomicArrayOfNulls<Action<E>>(arraySize)
    private val lock = atomic(false)

    private val rnd = { Random.nextInt(arraySize) }

    @Suppress("BooleanLiteralArgument")
    private fun await(action: Action<E>): E? {
        fun master(): E? {
            repeat(arraySize) { k ->
                fcArray[k].getAndUpdate {
                    it?.let { Result(it.run()) }
                }
            }

            lock.getAndSet(false)

            return action.run()
        }

        fun ifDone(index: Int): FCPriorityQueue<E>.Result? {
            return fcArray[index].getAndUpdate {
                when (it) {
                    is FCPriorityQueue<*>.Result -> return null
                    else -> it
                }
            } as? FCPriorityQueue<E>.Result
        }

        if (lock.compareAndSet(false, true)) {
            return master()

//            return (fcArray[index].getAndSet(null) as Result).x
        } else {
            var index: Int
            while (true) {
                index = rnd()
                if (fcArray[index].compareAndSet(null, action)) {
                    break
                }
            }

            while (true) {
                if (lock.compareAndSet(false, true)) {
                    ifDone(index)?.let { lock.getAndSet(false); return it.x }

                    return master()

//                    return (fcArray[index].getAndSet(null) as Result).x
                }

                ifDone(index)?.let { return it.x }
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return await(DEQ())
//        return q.poll()
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return await(PEEK())
//        return q.peek()
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        await(ENQ(element))
//        q.add(element)
    }
}
