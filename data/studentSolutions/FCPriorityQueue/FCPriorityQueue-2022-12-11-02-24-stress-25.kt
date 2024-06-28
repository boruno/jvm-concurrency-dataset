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

    private fun unlock() = locked.compareAndSet(expect = true, update = false)


    //FC_ARRAYS
    private val fcArray = atomicArrayOfNulls<(() -> Any?)?>(FC_ARRAY_SIZE)
    private val rets = atomicArrayOfNulls<Any?>(FC_ARRAY_SIZE)

    //Get free position in the array
    private fun put(func: () -> Any?): Int {
        var pos = Random.nextInt(0, FC_ARRAY_SIZE)
        while (!fcArray[pos].compareAndSet(null, func)) pos = (pos + 1) % FC_ARRAY_SIZE
        return pos
    }

    private fun isDone(i : Int) = rets[i].value != null

    //Go through array and complete all tasks
    private fun doAllTasks() {
        for (i in 0 until FC_ARRAY_SIZE) {
            //if job is not yet completed and exist we do it
            if (!isDone(i)) {
                val func = fcArray[i].value ?: continue
                rets[i].compareAndSet(null, func())
            }
        }
    }

    //Waiting for task completion or lock freedom
    private fun waiting(func: () -> Any?): Any? {
        val pos = put(func) //Getting free pos
        while (!tryLock()) { // Trying to catch the lock
            if (isDone(pos)) { // Check if the job is completed and in this case delete this job and return ans
                val ans = rets[pos].value
                rets[pos].compareAndSet(ans, null)
                fcArray[pos].compareAndSet(func, null)
                return ans
            }
        }
        if (!isDone(pos)) rets[pos].compareAndSet(null, func())
        val ans = rets[pos].value
        rets[pos].compareAndSet(ans, null)
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
        //If we took lock just do the func and then complete all in array
        val ans = q.poll()
        doAllTasks()
        unlock()
        ans
    } else {
        //Else wait
        val func = { q.poll() }
        waiting(func) as E?
    }


    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? = if (tryLock()) {
        //Same
        val ans = q.peek()
        doAllTasks()
        unlock()
        ans
    } else {
        //Same
        val func = { q.peek() }
        waiting(func) as E?
    }


    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        if (tryLock()) {
            //Same
            q.add(element)
            doAllTasks()
            unlock()
        } else {
            //Same
            val func = { q.add(element) }
            waiting(func)
        }
    }
}

private const val FC_ARRAY_SIZE = 10