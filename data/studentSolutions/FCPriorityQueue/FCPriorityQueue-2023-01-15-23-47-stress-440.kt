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
    }
    private inner class DEQ(): Action<E> {
        override fun run(): E? {
            return q.poll()
        }
    }
    private inner class PEEK(): Action<E> {
        override fun run(): E? {
            return q.peek()
        }
    }
    private inner class Result(val x: E?): Action<E> {
        override fun run(): E? {
            return x
        }
    }

    private val q = PriorityQueue<E>()

    private val fcArray = atomicArrayOfNulls<Action<E>>(2)
    private val lock = atomic(false)

    private val rnd = { Random.nextInt(2) }

    fun println(x: Any) {}

    @Suppress("BooleanLiteralArgument")
    private fun await(action: Action<E>): E? {
        fun master(): E? {
            val res = action.run()

            repeat(2) { k ->
                fcArray[k].getAndUpdate {
                    it?.let { Result(it.run()) }
                }
            }

            lock.getAndSet(false)

            return res
        }

        fun ifDone(index: Int): E? {
            return (fcArray[index].getAndUpdate {
                when (it) {
                    is FCPriorityQueue<*>.Result -> return null
                    else -> it
                }
            } as? FCPriorityQueue.Result)?.run()
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
                ifDone(index)?.let { println("${Thread.currentThread().id}> await + done: $it"); return it }

                if (lock.compareAndSet(false, true)) {
                    ifDone(index)?.let { println("${Thread.currentThread().id}> await + master + done: $it"); lock.getAndSet(false); return it }

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
