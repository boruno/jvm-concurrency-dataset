//package mpp.linkedlistset

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
            val node = findNode(element)
            if (node.element == element) {
                return false
            }
            val newNode = Node<E>(prev = node.prev, element = element, next = node)
            if (node.prev!!.casNext(node, newNode)) {
                node.next!!.casPrev(node, newNode)
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
            val node = findNode(element)
            if (node.element != element) {
                return false
            }
            if (node.removed.compareAndSet(false, true)) {
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        if (findNode(element).element == element) return true
        return false
    }

    private fun findNode(element: E): Node<E> {
        var curNode = head.value
        while (curNode.element != null && curNode.element!! < element) {
//            get new next node
            var nextNode = curNode.next
            if (nextNode!!.removed.value) {
//                if it's removed, try to help removing it
                nextNode.prev!!.casNext(nextNode, nextNode.next)
                nextNode.next!!.casPrev(nextNode, nextNode.prev)
            }
            curNode = nextNode
        }
        return curNode
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element
    val removed = atomic(false)

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