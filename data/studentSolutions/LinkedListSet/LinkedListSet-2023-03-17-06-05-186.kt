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
            var (node, next) = find(element)
//            node = next.prev!!
//            next = node.next!!
            if (next != last && next.element == element) {
                return false
            } else {
                val newNode = Node(node, element, next)
                if (node.casNext(next, newNode)) {
                    return true
                } else {
                    continue
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
            val node = find(element)[1]
            if (node == last || node.element != element) {
                return false
            } else {
                node.prev!!.casNext(node, node.next!!)
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val node = find(element)[1]
        return node != last && node.element == element
    }

    private fun find(element: E): List<Node<E>> {
        var curNode = first
        var nextNode = curNode.next!!
        while (nextNode != last && nextNode.element != element) {
            curNode = nextNode
            nextNode = nextNode.next!!
        }

        curNode.setNext(nextNode)
        nextNode.setPrev(curNode)
        return ArrayList<Node<E>>(listOf(curNode, nextNode))
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