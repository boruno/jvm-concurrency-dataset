package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(element = null, next = null)
    private val last = Node<E>(element = null, next = null)
    init {
        first.setNext(last)
    }

    private class Window<E : Comparable<E>>(var cur : Node<E>, var next : Node<E>)

    private fun findWindow(x : E) : Window<E> {
        while (true) {
            val window = Window(head.value, head.value.next!!)
            while (window.next.element < x) {
                window.cur = window.next
                if (window.cur.next != null) window.next = window.cur.next!!
                else return window
            }
            return window
        }
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
            val window = findWindow(element)
            if (window.next.element == element) return false
            val newNode = Node(element, window.next)
            if (window.cur.casNext(window.next, newNode)) return true
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
            if (window.next.element != element) return false
            if (window.cur.casNext(window.next, window.next.next)) return true
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        while (true) {
            val window = findWindow(element)
            return window.next.element == element
        }
    }
}

private class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    private val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }
    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
}