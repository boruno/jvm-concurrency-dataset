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

    private class Ans(val value: Any?)

    //FC_ARRAYS
    private val fcArray = atomicArrayOfNulls<(() -> Any?)?>(FC_ARRAY_SIZE)
    private val rets = atomicArrayOfNulls<Ans?>(FC_ARRAY_SIZE)

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
                if (!rets[i].compareAndSet(null, Ans(func()))) throw Error("wtf in doAllTasks")
            }
        }
    }

    //Waiting for task completion or lock freedom
    private fun waiting(func: () -> Any?, name: String): Any? {
        val pos = put(func) //Getting free pos
        while (!tryLock()) { // Trying to catch the lock
            if (isDone(pos)) { // Check if the job is completed and in this case delete this job and return ans
                val ans = rets[pos].value ?: throw Error("wtf1")
                println("for function $name calculated res:${ans.value}")
                if (!rets[pos].compareAndSet(ans, null)) throw Error("wtf in !tryLock")
                fcArray[pos].compareAndSet(func, null)
                return ans.value
            }
        }
        if (!isDone(pos)) if (!rets[pos].compareAndSet(null, Ans(func()))) throw Error("wtf in !isDone")
        val ans = rets[pos].value ?: throw Error("wtf2")
        if (!rets[pos].compareAndSet(ans, null)) throw Error("wtf in waiting")
        fcArray[pos].compareAndSet(func, null)
        doAllTasks()
        unlock()
        return ans.value
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
        waiting(func, "poll()") as E?
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
        waiting(func, "peek()") as E?
    }


    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        if (tryLock()) {
            //Same
            q.add(element)
            println("norm add() done")
            doAllTasks()
            unlock()
        } else {
            //Same
            val func = { q.add(element) }
            waiting(func, "add($element)")
        }
    }
}

private const val FC_ARRAY_SIZE = 10