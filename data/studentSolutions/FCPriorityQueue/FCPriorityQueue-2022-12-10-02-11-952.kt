@file:Suppress("UNCHECKED_CAST")

import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import kotlin.random.Random

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()

    //Lock
    private val locked: AtomicBoolean = atomic(false)

    private fun tryLock() = locked.compareAndSet(expect = false, update = true)

    private fun unlock() {
        locked.compareAndSet(expect = true, update = false)
    }

    //FC_ARRAYS
    private val fcArray = atomicArrayOfNulls<Any?>(FC_ARRAY_SIZE)
    private val isDone = BooleanArray(FC_ARRAY_SIZE) { false }
    private val rets = arrayOfNulls<Any?>(FC_ARRAY_SIZE)

    //Functions for FC_ARRAYS
    private fun put(func: () -> Any?): Int {
        var pos = Random.nextInt(0, FC_ARRAY_SIZE)
        while (!fcArray[pos].compareAndSet(null, func)) pos = (pos + 1) % FC_ARRAY_SIZE
        return pos
    }

    private fun doAllTasks() {
        for (i in 0 until FC_ARRAY_SIZE) {
            if (fcArray[i].value != null && !isDone[i]) {
                val func = fcArray[i].value as () -> Any?
                isDone[i] = true
                rets[i] = func()
            }
        }
    }

    private fun waiting(func: () -> Any?): Any? {
        val pos = put(func)
        while (!tryLock()) {
            if (isDone[pos]) {
                val ans = rets[pos] as E?
                isDone[pos] = false
                rets[pos] = null
                fcArray[pos].compareAndSet(func, null)
                return ans
            }
        }
        val ans = if (isDone[pos]) rets[pos] as E? else func()
        isDone[pos] = false
        rets[pos] = null
        fcArray[pos].compareAndSet(func, null)
        doAllTasks()
        unlock()
        return ans
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? = if (tryLock()) {
        val ans = q.poll()
        doAllTasks()
        unlock()
        ans
    } else {
        val func = { q.poll() }
        waiting(func) as E?
    }


    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? = if (tryLock()) {
        val ans = q.peek()
        doAllTasks()
        unlock()
        ans
    } else {
        val func = { q.peek() }
        waiting(func) as E?
    }


    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {

        if (tryLock()) {
            q.add(element)
            doAllTasks()
            unlock()
        } else {
            val func = { q.add(element) }
            waiting(func)
        }
    }
}

private const val FC_ARRAY_SIZE = 16