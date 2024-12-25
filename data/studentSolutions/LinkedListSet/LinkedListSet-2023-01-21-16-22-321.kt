//package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null, false)
    private val last = Node<E>(prev = first, element = null, next = null, false)
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
        var node = first
        while (true) {
            if (node.removed.value) {
                node = node.next!!
                continue
            }
            if (node.element == element) return false
            if (node === last) {
                val newNode = Node<E>(node.prev, element, last, false)
                if (node.prev!!.casNext(last, newNode)) {
                    last.casPrev(node.prev!!, newNode)
                    return true
                }
            }
            if (node.next!!.element > element) {
                val newNode = Node<E>(node, element, node.next, false)
                if (node.casNext(node.next, newNode)) {
                    node.next!!.casPrev(node, newNode)
                    return true
                }
            }
            node = node.next!!
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
        var node = first
        while (true) {
            if (!node.removed.value) {
                if (node.element == element) {
                    if (node.removed.compareAndSet(false, true)) {
                        if (node.prev!!.casNext(node, node.next)) {
                            node.next!!.casPrev(node, node.prev)
                            return true
                        }
                    }
                }
            }
            if (node.next == null) {
                return false
            }
            if (node.next!!.element > element) {
                return false
            }
            node = node.next!!
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var node = first
        while (node.next != null) {
            if (node.removed.value) {
                node = node.next!!
                continue
            }
            if (node.element == element) return true
            if (node.element > element) return false
            node = node.next!!
        }
        return false
    }

    data class Range<E : Comparable<E>>(val currentNode: Node<E>, val nextNode: Node<E>)
}

public class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?, removed: Boolean) {
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

    val removed = atomic(removed)
}