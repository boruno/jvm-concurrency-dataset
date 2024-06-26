package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = atomic(Node<E>(prev = null, element = null, next = null))
    private val last = atomic(Node(prev = first.value, element = null, next = null))
    init {
        first.value.setNext(last.value)
    }

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        var curNode: Node<E>? = first.value.next
        while (true) {
            while ((curNode != last.value) && (curNode?.element != element))
                curNode = curNode?.next
            if (curNode.element == element) {
                return false
            }
            val last = last.value
            val newNode = Node(prev = last.prev, element = element, next = last)
            if (last.prev!!.casNext(curNode.prev, newNode)) {
                if (this.last.compareAndSet(curNode, newNode)) {
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
        var curNode: Node<E>? = first.value.next
        while (true) {
            while ((curNode != last.value) && (curNode?.element != element))
                curNode = curNode?.next
            if (curNode == last.value)
                return false

            val prev = curNode.prev
            val next = curNode.next

            prev?.setNext(next)
            next?.setPrev(prev)
            return true
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var curNode: Node<E>? = first.value.next
        while ((curNode != last.value) && (curNode?.element != element))
            curNode = curNode?.next
        return curNode != last.value
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