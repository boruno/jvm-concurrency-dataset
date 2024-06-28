package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */

    private val delay = 50L;

    fun push(x: E) {
        val elim_pos = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        var shouldstop = false
        var popfound = false
        val myval = Pair("push", x)
        val myval2 = Pair("push-", x)
        while (true) {
            val oldval = eliminationArray[elim_pos].value
            if (oldval is Pair<*, *> && "push".equals(oldval.first)) {
                shouldstop = true
                break
            }
            if ("pop" == oldval) {
                if (eliminationArray[elim_pos].compareAndSet(oldval, myval2)) {
                    popfound = true
                    break
                }
                continue
            }
            if (eliminationArray[elim_pos].compareAndSet(null, myval)) {
                break
            }
        }
        if (popfound) {
            return
        }
        if (!shouldstop) {
            val time = LocalDateTime.now() + Duration.ofMillis(delay)
            while (LocalDateTime.now() < time) {
                val oldval = eliminationArray[elim_pos].value;
                if (oldval != myval && eliminationArray[elim_pos].compareAndSet("pop-", null)) {
                    return
                }
            }
            while (true) {
                val oldval = eliminationArray[elim_pos].value;
                if (oldval != myval && eliminationArray[elim_pos].compareAndSet("pop-", null)) {
                    return
                }
                if (oldval == myval && eliminationArray[elim_pos].compareAndSet(myval, null)) {
                    break
                }
            }
        }
        while (true) {
            val cur_top = top.value
            val new_top = Node<E>(x, cur_top)
            if (top.compareAndSet(cur_top, new_top)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val elim_pos = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        var shouldstop = false
        var pushfound = false
        var foundval : E? = null
        val myval = "pop"
        val myval2 = "pop-"

        while (true) {
            val oldval = eliminationArray[elim_pos].value
            if ("pop".equals(oldval)) {
                shouldstop = true
                break
            }
            if (oldval is Pair<*, *> && "push".equals(oldval.first)) {
                if (eliminationArray[elim_pos].compareAndSet(oldval, myval2)) {
                    pushfound = true
                    foundval = oldval.second as E?
                    break
                }
                continue
            }
            if (eliminationArray[elim_pos].compareAndSet(null, myval)) {
                break
            }
        }
        println("Z123")
        if (pushfound) {
            return foundval
        }
        if (!shouldstop) {
            val time = LocalDateTime.now() + Duration.ofMillis(delay)
            while (LocalDateTime.now() < time) {
                val oldval = eliminationArray[elim_pos].value;
                if (oldval != myval && oldval is Pair<*, *> && "push-".equals(oldval.first)
                    && eliminationArray[elim_pos].compareAndSet(oldval, null)) {
                    return oldval.second as E?
                }
            }
            while (true) {
                val oldval = eliminationArray[elim_pos].value;
                if (oldval != myval && oldval is Pair<*, *> && "push-".equals(oldval.first)
                    && eliminationArray[elim_pos].compareAndSet(oldval, null)) {
                    return oldval.second as E?
                }
                if (oldval == myval && eliminationArray[elim_pos].compareAndSet(myval, null)) {
                    break
                }
            }
        }

        while (true) {
            val cur_top = top.value
            val new_top = cur_top?.next
            if (top.compareAndSet(cur_top, new_top)) {
                return cur_top?.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT