//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

open class TreiberStack<E> {
    private val top = atomic<Node<E>?>(null)

    open fun push(x: E) {
        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)
            if (top.compareAndSet(curTop, newTop)) {
                return
            }
        }
    }

    open fun pop(): E? {
        while (true) {
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop))
                return curTop.x
        }
    }
}


class TreiberStackWithElimination<E> : TreiberStack<E>() {
    private val eliminationArray = atomicArrayOfNulls<Proxy<E>?>(ELIMINATION_ARRAY_SIZE)
    private val defaultProxy = Proxy<E>(null)

    override fun push(x: E) {
        val position = getRandomPosition()
        val onPosition = eliminationArray[position]
        val proxy = Proxy(x)
        if (!onPosition.compareAndSet(null, proxy))
            super.push(x)
        repeat(TIMEOUT_ITERATIONS) {
           if (onPosition.compareAndSet(defaultProxy, null))
               return
        }
        if (onPosition.compareAndSet(proxy, null))
            super.push(x)
        else
            onPosition.value = null
    }

    override fun pop(): E? {
        val position = getRandomPosition()
        val onPosition = eliminationArray[position]
        val value = onPosition.value ?: return super.pop()
        if (value != defaultProxy && onPosition.compareAndSet(value, null)) {
            return value.x
        }
        return super.pop()
    }

    private class Proxy<E>(val x: E?)

    private fun getRandomPosition(): Int = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val TIMEOUT_ITERATIONS = 10