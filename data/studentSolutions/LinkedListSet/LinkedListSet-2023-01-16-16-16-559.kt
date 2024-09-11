package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val last = Node<E>(element = null, next = null)
    private val first = Node(element = null, next = last)

    /*init {
        first.setNext(last)
    }*/
    private fun findWindow(element: E): Window<E> {
        var cur = first
        var next = first.next!!

        while (next !== last && next.element < element) {
            val node = next.next

            if (node is Removed) {
                if (!cur.casNext(next, Node(node.element, node.next)))
                    return findWindow(element)

                next = node
            } else {
                cur = next
                next = cur.next!!
            }
        }

        return Window(cur, next)
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
            val (last, next) = findWindow(element)

            if (next !== last && element == next.element)
                return false

            val cur = Node(element, next)

            if (last.casNext(next, cur))
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
        while (true) {
            val (_, node) = findWindow(element)

            if (node === last || element != node.element)
                return false

            if (node.casNext(node, Removed(node.element, node.next)))
                return true
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val (_, node) = findWindow(element)

        if (node === last) {
            return false
        }

        return element == node.element
    }
}

private open class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    private val _next = atomic(next)
    val next get() = _next.value
    /*fun setNext(value: Node<E>?) {
        _next.value = value
    }*/
    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
}

private class Removed<E : Comparable<E>>(element: E?, next: Node<E>?) : Node<E>(element, next)

private data class Window<E : Comparable<E>>(val cur: Node<E>, val next: Node<E>)