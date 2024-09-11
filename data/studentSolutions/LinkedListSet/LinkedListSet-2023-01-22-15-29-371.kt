package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(element = null, next = null)
    private val last = Node<E>(element = null, next = null)

    init {
        first.setNext(last)
    }

    private val head = atomic(first)

    private fun findWindow(element: E): Window<E> {
        var cur = head.value
        while (cur.next != null && cur.next != last && cur.next?.element!! < element) {
            cur = cur.next!!
        }
        return Window(cur, cur.next!!)
    }

    private class Window<E : Comparable<E>>(val cur: Node<E>, val next: Node<E>) {
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
            val window = findWindow(element)
            if (window.next != last && window.next.element === element) {
                return false
            }
            if (window.cur.casNext(window.next, Node(element = element, next = window.next))) {
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
            val window = findWindow(element)
            if (window.next != last && window.next.element != element) {
                return false
            }
            window.next.casRemoved(false, true)
            if (!window.cur.getRemoved().value && window.cur.casNext(window.next, window.next.next)) {
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val window = findWindow(element)
        if (window.next != last) {
            return window.next.element === element
        }
        return false
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>? = null, element: E?, next: Node<E>?) {
    private val removed = atomic(false)
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    private val _prev = atomic(prev)
    val prev get() = _prev.value
    fun setPrev(value: Node<E>?) {
        _prev.value = value
    }

    fun casRemoved(expected: Boolean, update: Boolean) = removed.compareAndSet(expected, update)
    fun getRemoved() = removed

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