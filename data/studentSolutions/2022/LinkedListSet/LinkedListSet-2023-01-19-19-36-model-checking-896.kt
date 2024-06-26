package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null)
    private val last = Node<E>(prev = first, element = null, next = null)
    init {
        first.setNext(last)
    }

    private val head = atomic(first)

    private fun isGreater(node: Node<E>, element: E): Boolean {
        if (node == first) return false
        if (node == last) return true
        return node.element!! > element
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
            val (prev, next) = findWindow(element)
            if (next.element == element) return false
            val node = Node(prev, element, next)
            if (prev.casNext(next, node)) {
                next.casPrev(prev, node)
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
            val (prev, next) = findWindow(element)
            if (next.element != element) return false
            if (prev.casNext(next, RemovedNode(next))) return true
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val (prev, next) = findWindow(element)
        return next.element == element
    }

    private fun findWindow(key: E): Pair<Node<E>, Node<E>> {
        while (true) {
            var curr = head.value
            var next = curr.next ?: continue

            while (true) {
                if (next is RemovedNode<E>) {
                    if (curr.casNext(next, next.next)) {
                        next = next.next!!
                        continue
                    }
                    next = curr.next ?: continue
                } else if (isGreater(next, key)) {
                    break
                } else {
                    curr = next
                    next = curr.next ?: continue
                }
            }

            if (curr !is RemovedNode<E>) {
                return Pair(curr, next)
            }
        }
    }
}

private class RemovedNode<E : Comparable<E>>(node: Node<E>) : Node<E>(node.prev, node.element, node.next)

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