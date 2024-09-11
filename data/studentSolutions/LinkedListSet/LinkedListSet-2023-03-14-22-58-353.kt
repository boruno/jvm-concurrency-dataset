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
            var curr = head.value.next!!
            while (curr.element != null && curr.element!! < element) {
                curr = curr.next!!
            }
            if (curr.element == element) {
                return false
            }
            var prevv = curr.prev!!
            while (prevv.next!!.element != null && prevv.next!!.element!! < element)
                prevv = prevv.next!!

            val node = Node(prev = prevv, element = element, next = curr)

            if (prevv.casNext(curr, node)) {
                curr.casPrev(curr, node)
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
            var curr = head.value.next!!
            while (curr.element!! < element) {
                curr = curr.next!!
            }
            if (curr.next == null || curr.element != element) {
                return false
            }
            if (curr.deleted.compareAndSet(curr.deleted.value, true)) {
                curr.prev!!.casNext(curr, curr.next)
                curr.next!!.casNext(curr, curr.prev)
                return true
            }
        }

    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var curr = head.value.next!!
        while (curr.element!! < element) {
            curr = curr.next!!
        }
        return curr.element == element
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!
    val deleted = atomic(false)

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