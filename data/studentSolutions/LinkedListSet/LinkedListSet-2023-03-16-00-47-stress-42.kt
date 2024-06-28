package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null)
    private val last = Node<E>(prev = first, element = null, next = null)
    init {
        first.setNext(last)
    }

    private val head = atomic(first)

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        while (true) {
            val (cur, next) = findWindow(element)
            if (next.element == element)
                return false

            val node = Node(null, element, next)
            if (cur.casNext(next, node)) {
                if (cur.isAlive.value)
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
            val (cur, next) = findWindow(element)
            if (next.element != element)
                return false

            if (next != last)
                next.isAlive.value = false
            if (cur.casNext(next, next.next!!)) {
                if (cur.isAlive.value)
                    return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val nxt = findWindow(element).second
        return nxt != last && nxt.element == element
    }

    private fun findWindow(element: E): Pair<Node<E>, Node<E>> {
        var cur: Node<E> = head.value
        var next: Node<E> = last
        while (cur != last) {
            next = cur.next!!
            if ((cur == first || cur.element < element) && (next == last || element <= next.element)) {
                break
            }
            cur = next
        }
        return Pair(cur, next)
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
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

    val isAlive: AtomicBoolean = atomic(true)
}