//package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val last = Node<E>(prev = null, element = Int.MAX_VALUE as E, next = null)
    private val first = Node<E>(prev = null, element = Int.MIN_VALUE as E, next = last)
    init {
        last.setPrev(first)
    }

    private val head = atomic(first)

    private fun findNode(element: E): Node<E>{
        var curNode = head.value
        while (element > curNode.element) {
            if (curNode.next == null){
                return curNode
            }
            curNode = curNode.next!!
        }
        return curNode
    }
    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        while (true){
            val node = findNode(element)
            if (node.element == element)
                return false
            if (node.prev?.casNext(node, Node<E>(prev = node.prev, element = element, next = node)) == true)
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
        while(true) {
            val node = findNode(element)
            if (node.element != element)
                return false
            if (node.prev?.casNext(node, node.next) == true) {
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val node = findNode(element)
        if (node.element == element){
            return true
        }
        return false
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    var removed = false
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