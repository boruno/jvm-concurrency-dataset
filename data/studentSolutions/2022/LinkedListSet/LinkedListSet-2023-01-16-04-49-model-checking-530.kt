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
        if (contains(element))
            return false

        while (true) {
            val subset = findSubset(element)
            val node = Node(subset.first, element, subset.second)

            if (subset.first.casNext(subset.second, node))
                return true
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
        TODO("implement me")
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val subset = findSubset(element)

        if (subset.first.element == element)
            return true

        if (subset.second.element == element)
            return true

        return false
    }

    private fun findSubset(element: E): NodeSubset<E> {
        var first: Node<E> = head.value
        var second: Node<E> = first.next!!

        while (true) {
            if (second.element == null)
                break

            if (element < second.element!!)
                break

            first = second
            second = second.next!!
        }

        return NodeSubset(first, second)
    }

    private inner class NodeSubset<E : Comparable<E>>(val first: Node<E>, val second: Node<E>)
}

open class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element

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

private class DeletedNode<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) : Node<E>(prev, element, next)