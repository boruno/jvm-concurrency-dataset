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
            val (first, second) = findWindow(element)
            if (second != null && second.element == element) {
                return false
            }
            val node = Node(element = element, next = second)
            if (first.casNext(second, node)) {
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
            val (_, nodeForRemove) = findWindow(element)
            if (nodeForRemove === null || element != nodeForRemove.element) {
                return false
            }
            if (nodeForRemove.casNext(
                    nodeForRemove, Wrapper(nodeForRemove.element, nodeForRemove.next)
                )
            ) {
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val (_, second) = findWindow(element)
        if (second == null) {
            return false
        }
        return second.element == element
    }

    private fun findWindow(element: E): Pair<Node<E>, Node<E>?> {
        while (true) {
            var cur = head.value
            var next = head.value.next ?: return (cur to null)
            while (true) {
                if (cur.element < element && element <= next.element) {
                    return (cur to next)
                } else {
                    cur = next.also { next = next.next ?: return (cur to null) }
                }
            }
        }
    }
}

private open class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    private val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }

    val isRemoved get() = this is Wrapper

    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
}

private class Wrapper<E : Comparable<E>>(element: E?, next: Node<E>?): Node<E>(element, next)