//package mpp.linkedlistset

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
            var previous = first
            var next = previous.next
            while (next != last && next.element <= element) {
                previous = next
                next = previous.next
            }
            if (previous != first && previous.element == element) {
                return false
            }
            val node = Node(element, next)
            if (previous.casNext(Pair(next, false), Pair(node, false))) {
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
        var previous = first
        var current = previous.next
        while (current != last && current.element < element) {
            previous = current
            current = previous.next
        }
        if (current == last || current.element != element) {
            return false
        }
        if (!current.setRemoved()) return false
        val next = current.next
        while (true) {
            if (previous.casNext(Pair(current, false), Pair(next, false))) return true
            previous = first
            while (previous != last && previous.next != current) {
                previous = previous.next
            }
            if (previous == last) return true
        }

    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var previous = first
        var next = previous.next
        while (next != last && next.element <= element) {
            previous = next
            next = previous.next
        }
        return previous != first && previous.element == element && !previous.removed
    }
}

private class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    private val _next = atomic(Pair(next, false))
    val next get() = _next.value.first!!
    val removed get() = _next.value.second

    fun setNext(value: Node<E>?) {
        _next.getAndUpdate { prevValue ->
            Pair(value, prevValue.second)
        }
    }

    fun setRemoved(): Boolean {
        return _next.getAndUpdate { prevValue ->
            Pair(prevValue.first, true)
        }.second
    }

    fun casNext(expected: Pair<Node<E>?, Boolean>, update: Pair<Node<E>?, Boolean>) =
        _next.compareAndSet(expected, update)
}