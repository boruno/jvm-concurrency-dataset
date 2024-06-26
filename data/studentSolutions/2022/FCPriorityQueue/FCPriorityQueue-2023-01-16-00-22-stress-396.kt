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

//    fun println(x: Any) {}

    @Suppress("BooleanLiteralArgument")
    private fun await(action: Action<E>): E? {
        fun master(): E? {
            val res = action.run()

            repeat(arraySize) { k ->
                fcArray[k].getAndUpdate {
                    it?.let { Result(it.run().also { x -> println("${Thread.currentThread().id}> helper $it") }) }
                }
            }

            lock.getAndSet(false)

            return res
        }

        fun ifDone(index: Int): FCPriorityQueue<E>.Result? {
            return fcArray[index].getAndUpdate {
                when (it) {
                    is FCPriorityQueue<*>.Result -> return null
                    else -> it
                }
            } as? FCPriorityQueue<E>.Result
        }

        println("${Thread.currentThread().id}> $action")

        if (lock.compareAndSet(false, true)) {
            println("${Thread.currentThread().id}> master")
            return master()
        } else {
            println("${Thread.currentThread().id}> array")
            var index: Int
            while (true) {
                index = rnd()
                if (fcArray[index].compareAndSet(null, action)) {
                    break
                }
            }

            println("${Thread.currentThread().id}> $index")

            while (true) {
                ifDone(index)?.let { println("${Thread.currentThread().id}> await + done: $it"); return it.x }

                if (lock.compareAndSet(false, true)) {
                    ifDone(index)?.let { println("${Thread.currentThread().id}> await + master + done: $it"); lock.getAndSet(false); return it.x }

                    println("${Thread.currentThread().id}> await + master")
                    return master()
                }
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
