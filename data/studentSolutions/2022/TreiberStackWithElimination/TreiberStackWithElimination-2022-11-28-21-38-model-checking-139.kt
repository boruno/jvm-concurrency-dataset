package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random


class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
//        TODO("implement me")
        println("push " + x)
        var elimIndex = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        var success = false
        for (step in 0 until STEP_SIZE) {
            elimIndex = (elimIndex + step) % ELIMINATION_ARRAY_SIZE
            if (eliminationArray[elimIndex].compareAndSet(null, x)) {
                success = true
                break
            }
        }
        if (success) {
            for (i in 0 until WAIT_SIZE) {
                if (eliminationArray[elimIndex].compareAndSet(null, null)) {
                    println("elim_push_1")
                    return
                }
            }

            if (!eliminationArray[elimIndex].compareAndSet(x, null)) {
                println("elim_push_2")
                return
            }
        }

        var old: Node<E>?
        var new: Node<E>
        while (true) {
            old = top.value
            new = Node(x, old)
            if (top.compareAndSet(old, new)) {
                break
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
//        TODO("implement me")
        println("pop")
        var elimIndex = Random.nextInt(ELIMINATION_ARRAY_SIZE)

        for (step in 0 until STEP_SIZE) {
            elimIndex = (elimIndex + step) % ELIMINATION_ARRAY_SIZE
            for (i in 0 until WAIT_SIZE) {
                @Suppress("UNCHECKED_CAST")
                val element: E? = eliminationArray[elimIndex].getAndSet(null) as E
                if (element != null) {
                    println("elim")
                    println(element)
                    return element
                }
            }
        }

        var old: Node<E>?
        var new: Node<E>?
        while (true) {
            old = top.value
            if (old == null) {
                return null
            }
            new = old.next
            if (top.compareAndSet(old, new)) {
                break
            }
        }
        println(old!!.x)
        return old!!.x
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val WAIT_SIZE = 4
private const val STEP_SIZE = ELIMINATION_ARRAY_SIZE