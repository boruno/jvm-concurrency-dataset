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
    fun add(element: E): Boolean { //null f(null) 1 2 3 6 l(null) null +5
        val new = Node(null, element, null)
        while (true) {
            if (contains(element)) return false
            var cur: Node<E>? = first.next
            while (cur?.element != null && cur.element < element) {
                cur = cur.next
            }
            new.setNext(cur)
            if (cur?.element == null) {
                val pr = last.prev
                new.setPrev(pr)
                new.setNext(last)
                if (last.casPrev(null, new)) {
                    pr!!.casNext(last, new)
                    return true
                }

            } else {
                val pr = cur.prev
                new.setPrev(pr)
                new.setNext(cur)
                if (cur.casPrev(null, new)) {
                    pr!!.casNext(cur, new)
                    return true
                }
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
            var cur: Node<E>? = first.next
            while (cur?.element != null && cur.element < element) {
                cur = cur.next
            }
            if (cur?.element != element) return false
            if (cur.next!!.casPrev(cur, cur.prev)) {
                cur.prev!!.casNext(cur, cur.next)
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var e = first.element
        var next = first.next
        while (e <= element && next != null) {
            if (e == element) return true
            e = next.element
            next = next.next
        }
        if (e == element) {
            return true
        }
        return false
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
}