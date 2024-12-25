//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.math.ceil
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    fun simplePush(x: E) {
        while(true) {
            val curTop = top.value
            val newTop = Node(x, curTop)
            if(top.compareAndSet(curTop, newTop)){
                return
            }
        }
    }

    fun simplePop(): E? {
        while(true) {
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if(top.compareAndSet(curTop, newTop)) {
                return curTop.x
            }
        }
    }
    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val randomIndex = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
        if(!eliminationArray[randomIndex].compareAndSet(null, x)) {
            simplePush(x)
            return
        }
        for(i in 0..10) {
            if(eliminationArray[randomIndex].compareAndSet(KEK, null))
                return
        }
        if(eliminationArray[randomIndex].compareAndSet(x, null)) {
            simplePush(x)
        } else {
            eliminationArray[randomIndex].compareAndSet(KEK, null)
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    @Suppress("UNCHECKED_CAST")
    fun pop(): E? {
        val randomIndex = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
        val value = eliminationArray[randomIndex].value
        if(value == null || value == KEK) {
            return simplePop()
        }
        if(!eliminationArray[randomIndex].compareAndSet(value, KEK)) {
            return simplePop()
        }
        return value as E?
    }
}

private object KEK: Any()

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT