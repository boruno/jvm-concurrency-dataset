//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

/**
 * @author :Цветков Николай
 */
class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        var index = -1;
        // is there a free elimination spot?
        for (i in 0 until eliminationArray.size - 1) {
            val curElEl = eliminationArray[index].value;
            if (curElEl != null) {
                index = i;
                break;
            }
        }
        // if there is, put x there and wait
        if (index != -1) {
            var curElEl = eliminationArray[index].value;
            eliminationArray[index].compareAndSet(curElEl, x)
            for (i in 0 until 100) {
                curElEl = eliminationArray[index].value;
                if (curElEl == null) {
                    return
                }
            }
            eliminationArray[index].compareAndSet(curElEl, null);
        }
        // waited too long or no free space
        while (true) {
            val curTop = top.value;
            val newTop = Node<E>(x, curTop);
            if (top.compareAndSet(curTop, newTop)) {
                return;
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var index = -1;
        // is there a free elimination spot?
        for (i in 0 until eliminationArray.size - 1) {
            for (j in 0 until 100) {
                val curElEl = eliminationArray[index].value;
                if (curElEl != null) {
                    index = i;
                    break;
                }
            }
        }
        // if there is, get x
        if (index != -1) {
            val curElEl = eliminationArray[index].value;
            var ans = curElEl
            eliminationArray[index].compareAndSet(curElEl, null)
            return ans as E;
        }
        // no elimination element or too much wait
        while (true) {
            val curTop = top.value;
            val newTop = curTop?.next;
            if (top.compareAndSet(curTop, newTop)) {
                return curTop?.x;
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT