package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(element = null, next = null)
    private val last = Node<E>(element = null, next = null)
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
            var cur = head.value
            var next = cur.next!!
            while (true) {
                if((cur == head.value || cur.element < element) && (next.next == null || element <= next.element)) {
                    if (element == next.element ) {
                        return false
                    }
                    val node = Node(element, next)
                    if (cur.casNext(next, node)) {
                        return true
                    }
                    break
                }
                cur = next
                next = next.next!!
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
            var cur = head.value
            var next = cur.next!!
            while (true) {
                if((cur == head.value || cur.element < element) && (next.next == null || element <= next.element)) {
                    if (element != next.element ) {
                        return false
                    }
                    next.markRemoved()
                    if (cur.casNext(next, next.next)) {
                        return true
                    }
                    break
                }
                cur = next
                next = next.next!!
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var cur = head.value
        while(cur.next != null) {
            if (cur.element == element) return true
            cur = cur.next!!
        }
        return false
    }
}

private class Removed<E : Comparable<E>>(node: Node<E>): Node<E>(node.element, node.next)

private open class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    private val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }
    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)

    fun markRemoved() {
        setNext(Removed(next!!))
    }
}