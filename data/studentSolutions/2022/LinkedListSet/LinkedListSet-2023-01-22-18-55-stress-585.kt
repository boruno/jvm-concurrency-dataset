package mpp.linkedlistset

import kotlinx.atomicfu.atomic

class LinkedListSet<E> {
    private val first = Node<E>(element = null, next = null)

    private val head = atomic(first)

    fun test() {
    }

    private fun findWindow(x: Int): Window<E> {
        retry@ while (true) {
            var cur = first
            var next = first.next.value // head is not deleted
            while (true) {
                val an = next!!.next.value
                if (an!!.element == null) {
                    cur.next.compareAndSet(next, an)
                    continue@retry
                } else {
                    val afterNext = an
                    if (cur.element!! < x && x <= next.element!!) {
                        return Window(cur, next, afterNext)
                    }
                    cur = next
                    next = afterNext
                }
            }
        }
    }


    private fun checkKey(x: Int) {
        require(!(x == Int.MIN_VALUE || x == Int.MAX_VALUE))
    }

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: Int): Boolean {
        //checkKey(element)
        while (true) {
            val w = findWindow(element)
            if (w.next?.element == element) {
                return false
            }
            if (w.cur!!.next.compareAndSet(w.next, Node(element, w.next))) {
                return true
            }
        }
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: Int): Boolean {
        checkKey(element)
        while (true) {
            val w = findWindow(element)
            if (w.next!!.element != element) {
                return false
            }
            if (w.next.next.compareAndSet(w.after, Node(null, null))) {
                w.cur!!.next.compareAndSet(w.next, w.after)
                return true
            }
        }
    }


    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: Int): Boolean {
        val w = findWindow(element)
        return w.next!!.element == element;
    }
}

private class Node<E>(element: Int?, next: Node<E>?) {
    var element = element // `null` for the first and the last nodes
    val next = atomic(next)
}


private class Window<E> (val cur: Node<E>?, val next: Node<E>?, val after: Node<E>?)