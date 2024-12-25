//package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null)
    private val last = Node<E>(prev = first, element = null, next = null)
    init {
        first.setNext(last)
    }

    private val head = atomic(first)

    private fun findWindow(element: E): Pair<Node<E>, Node<E>> {
        var prev: Node<E>?
        var cur: Node<E>?
        var next: Node<E>?

        while (true) {
            prev = head.value
            cur = prev.next
            var cont = false
            while (true) {
                next = cur!!.next
                if (next != null) {
                    while (cur is MarkedNode<E>) {
                        if (!prev!!.casNext(cur, next)) {
                            cont = true
                            break
                        }
                        cur = next
                        next = cur!!.next
                    }
                }
                if (cont) {
                    continue
                }
                if (cur == last || (cur != null && cur.element >= element)) {
                    return prev!! to cur
                }
                prev = cur
                cur = next
            }
        }
    }

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        while (true) {
            val (prev, cur) = findWindow(element)
            if (cur == last) {
                val nodeToInsert = Node<E>(prev, element, last)
                if (prev.casNext(cur, nodeToInsert)) {
                    return true
                }
            }
            if (cur.element == element) {
                return false
            }
            val nodeToInsert = Node<E>(prev, element, cur)
            if (prev.casNext(cur, nodeToInsert)) {
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
    fun remove(element: E): Boolean {
        while (true) {
            val (prev, cur) = findWindow(element)
            if (cur == last) {
                return false
            }
            if (cur.element != element) {
                return false
            }
            val next = cur.next
            val curMarked = MarkedNode<E>(prev, cur.element, next)
            if (!prev.casNext(cur, cur)) {
                continue
            }
            prev.casNext(curMarked, next)
            return true
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val (_, cur) = findWindow(element)
        if (cur == last) {
            return false
        }
        return cur.element == element
    }
}

private class MarkedNode<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) : Node<E>(prev, element, next)

private open class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    private val _prev = atomic(prev)
    val prev get() = _prev.value
    fun setPrev(value: Node<E>?) {
        _prev.value = value
    }
    fun casPrev(expected: Node<E>?, update: Node<E>?) =
        _prev.compareAndSet(expected, update)

    private val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }
    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
}