//package mpp.linkedlistset

import kotlinx.atomicfu.*
import java.util.Objects

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(element = Integer.MIN_VALUE as E, next = null)
    private val last = Node<E>(element = Integer.MAX_VALUE as E, next = null)

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
        //  a[i] < x <= a[i+1]

        while (true) {
            var current = first
            while (current.next != last && element > current.next!!.element) {
                current = current.next!!
            }
            if (current.next!!.element == element) {
                return false
            }
            val next = current.next
            val newNode = Node(element, next)
            if (current.casNext(next, newNode)) {
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
            var current = first
            while (current.next != last && element > current.next!!.element) {
                current = current.next!!
            }
            if (current.next!!.element != element) {
                return false
            }
            val next = current.next
            current.casNext(next, KilledNode(next!!))
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var current = first
        while (current.next != last && element > current.next!!.element) {
            current = current.next!!
        }

        return current.next!!.element == element
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
    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
}

private class KilledNode<E : Comparable<E>>(node: Node<E>) : Node<E>(node.element, node.next!!) {

}