//package mpp.stackWithElimination

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic


class TreiberStackWithElimination<E> {
    private class Node<E> constructor(x: Int, next: Node<E>?) {
        val next: AtomicRef<Node<E>?>
        val x: Int

        init {
            this.next = atomic(next)
            this.x = x
        }
    }

    // head pointer
    private val head = atomic<Node<E>?>(null)
    fun push(x: Int) {
        while (true) {
            val node = Node(x, head.value)
            if (head.compareAndSet(node.next.value, node)) break
        }
    }

    fun pop(): Int {
        while (true) {
            val node = head.value ?: return Int.MIN_VALUE
            if (head.compareAndSet(node, node.next.value)) return node.x
        }
    }
}
/*
class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Pair<String, E?>?>(ELIMINATION_ARRAY_SIZE)

    */
/**
     * Adds the specified element [x] to the stack.
     *//*

    fun push(x: E) {
        val eliminationIndex = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        var puttedToElimination = false
        // цикл ограничен максимальным числом итераций, потому что
        // если elimination долгий нужно пытаться честно вставить элемент в стек
        for(i in 0..MAX_ELIMINATION_ITERATIONS) {
            val eliminationElement = eliminationArray[eliminationIndex]
            if(eliminationElement.compareAndSet(null, Pair("PROCESSING", x))) {
                puttedToElimination = true
                break
            }
        }

        if (puttedToElimination) {
            for(i in 0..MAX_ELIMINATION_ITERATIONS) {
                if (eliminationArray[eliminationIndex].compareAndSet(Pair("DONE", null), null)) return
            }
            val eliminationElement = eliminationArray[eliminationIndex]
            eliminationArray[eliminationIndex].compareAndSet(eliminationElement.value, null);
        }


        while (true) {
            val currTop = top.value
            val newTop = Node(x, currTop)
            if (top.compareAndSet(currTop, newTop)) return
        }
    }

    */
/**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     *//*

    fun pop(): E? {
        val eliminationIndex = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        for (i in 0..MAX_ELIMINATION_ITERATIONS) {
            val x = eliminationArray[eliminationIndex].value
            if (x?.second != null) {
                if (eliminationArray[eliminationIndex].compareAndSet(x, Pair("DONE", null))) {
                    return x.second
                }
            }
        }

        while (true) {
            val currTop = top.value ?: return null
            val newTop = currTop.next
            if (top.compareAndSet(currTop, newTop)) return currTop.x
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val MAX_ELIMINATION_ITERATIONS = 6*/
