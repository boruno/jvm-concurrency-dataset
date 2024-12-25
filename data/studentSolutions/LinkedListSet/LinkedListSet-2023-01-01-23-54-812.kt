//package mpp.linkedlistset

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicMarkableReference

class LinkedListSet<E : Comparable<E>> {
//    private val first = Node<E>(element = null, next = null)
//    private val last = Node<E>(element = null, next = null)
//
////    init {
////        first.setNext(last)
////    }

    private val head = Node(Int.MIN_VALUE, Node(null, null))

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: Int): Boolean {
        while (true) {
            val w = findWindow(element)
            if (w.next!!.element == element) return false
            if (w.cur!!.casNext(w.next, Node(element, w.next), false, false)) {
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
        while (true) {
            val w = findWindow(element)
            if (w.next!!.element != element) {
                return false
            }
            val node = w.next!!.next
            if (w.next!!.casNext(node, node, false, true)) {
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
        return w.next!!.element == element
    }

    private class Window<E : Comparable<E>> {
        var cur: Node<E>? = null
        var next: Node<E>? = null
    }

    private fun findWindow(x: Int): Window<Int> {
        val w = Window<Int>()
        val mark = BooleanArray(1)
        retry@ while (true) {
            w.cur = head
            w.next = w.cur!!.next
            while (true) {
                var node = w.next!!._next[mark]
                while (mark[0]) {
                    if (!w.cur!!.casNext(w.next, node, false, false)) {
                        continue@retry
                    }
                    w.next = node
                    node = w.next!!._next[mark]
                }
                if (w.next!!.element < x) {
                    w.cur = w.next
                    w.next = w.cur!!.next
                } else {
                    break
                }
            }
            return w
        }
    }
}


private class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    val _next = AtomicMarkableReference(next, false)
    val next get() = _next.reference

//    fun setNext(value: Node<E>?) {
//        _next.(value)
//    }
    fun casNext(
        expected: Node<E>?, update: Node<E>?, expectedMark: Boolean, newMark: Boolean
    ): Boolean =
        _next.compareAndSet(expected, update, expectedMark, newMark)
}
