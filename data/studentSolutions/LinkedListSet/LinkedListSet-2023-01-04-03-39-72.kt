//package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null)
    private val last = Node(prev = first, element = null, next = null)
    init {
        first.setNext(last)
    }

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        var curNode: Node<E>? = first.next
        while ((curNode != last) && (curNode?.element != element))
            curNode = curNode?.next
        if (curNode == last) {
            val newNode = Node(prev = last.prev, element = element, next = last)
            last.prev?.setNext(newNode)
            last.setPrev(newNode)
            return true
        }
        return false
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: E): Boolean {
        var curNode: Node<E>? = first.next
        while ((curNode != last) && (curNode?.element != element))
            curNode = curNode?.next
        if (curNode == last)
            return false

        val prev = curNode.prev
        val next = curNode.next

        prev?.setNext(next)
        next?.setPrev(prev)
        return true
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var curNode: Node<E>? = first.next
        while ((curNode != last) && (curNode?.element != element))
            curNode = curNode?.next
        return curNode != last
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