//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom
import java.util.Timer

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Operation<E?>?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (exganger(x, "push") != null){
            return
        }
        while(true) {
            val curTop = top.value
            val newTop = Node<E>(x, curTop)
            if (top.compareAndSet(curTop, newTop)) {
                return
            }
        }
    }

    fun exganger(x: E?, command: String): E? {
        val start = System.currentTimeMillis()
        val end = start + 30 * 1000
        while (System.currentTimeMillis() < end) {
            val randomInteger = ThreadLocalRandom.current().nextInt() % 2
            val waitOp = eliminationArray[randomInteger].value
            if (waitOp != null) {
                if (command != waitOp.name) {
                    if (x == null) {
                        if (eliminationArray[randomInteger].compareAndSet(waitOp, null)) {
                            return waitOp.x
                        }
                        return null
                    }
                    if (eliminationArray[randomInteger].compareAndSet(waitOp, null)) {
                        return x
                    }
                } else {
                    if (eliminationArray[randomInteger].compareAndSet(waitOp, null)) {
                        return null
                    }
                }
            } else {
                val newOP = Operation<E?>(command, x)
                if (eliminationArray[randomInteger].compareAndSet(null, newOP)) {
                    return null
                }
            }
        }
        return null
    }
    
    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val answer = exganger(null, "pop")
        if (answer != null){
            return answer
        }
        while(true) {
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop.x
            }
        }
    }

    fun main(args: Array<String>){
        val f = TreiberStackWithElimination<Int>()
        f.push(5)
        f.pop()
    }
}

fun main(args: Array<String>){
    val f = TreiberStackWithElimination<Int>()
    f.push(5)
    f.pop()
}

private fun <E>Timer.schedule(i: E, function: () -> Any?): E?{
    return i
}

private class Node<E>(val x: E, val next: Node<E>?)

private class Operation<E>(val name: String, val x: E)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT