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
    private val fcArray = atomicArrayOfNulls<Pair<(() -> Any?), Ans?>?>(FC_ARRAY_SIZE)

    //Get free position in the array
    private fun put(func: () -> Any?): Int {
        var pos = Random.nextInt(0, FC_ARRAY_SIZE)
        while (!fcArray[pos].compareAndSet(null, Pair(func, null))) pos = (pos + 1) % FC_ARRAY_SIZE
        return pos
    }

    private fun isDone(i : Int) = fcArray[i].value?.second != null

    //Go through array and complete all tasks
    private fun doAllTasks() {
        for (i in 0 until FC_ARRAY_SIZE) {
            //if job is not yet completed and exist we do it
            if (!isDone(i)) {
                val func = fcArray[i].value?.first ?: continue
                val exp = Pair(func, null)
                val result = Pair(func, Ans(func()))
                if (!fcArray[i].compareAndSet(exp, result)) throw Error("wtf in doAllTasks")
            }
        }
    }

    //Waiting for task completion or lock freedom
    private fun waiting(func: () -> Any?, name: String): Any? {
        val pos = put(func) //Getting free pos
        while (!tryLock()) { // Trying to catch the lock
            if (isDone(pos)) { // Check if the job is completed and in this case delete this job and return ans
                val ans = fcArray[pos].value?.second ?: throw Error("wtf1")
//                println("for function $name calculated res:${ans.value}")
                val exp = Pair(func, ans)
                if (!fcArray[pos].compareAndSet(exp, null)) throw Error("wtf in !tryLock")
                return ans.value
            }
        }
        val ans = func()
        val exp = fcArray[pos].value
        if (!fcArray[pos].compareAndSet(exp, null)) throw Error("wtf in waiting")
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