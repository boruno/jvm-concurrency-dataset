package mpp.linkedlistset

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
        while (true) {
            val range = findRange(element)
            if (range.nextNode.element == element) {
                return false
            }
            val newNode = Node<E>(range.currentNode, element, range.nextNode, false)
            if (range.currentNode.casNext(range.nextNode, newNode)) {
                range.nextNode.casPrev(range.currentNode, newNode)
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
            val range = findRange(element)
            if (range.nextNode.element != element) {
                return false
            }
            if (range.nextNode.removed.compareAndSet(false, true)) {
                range.nextNode.prev!!.casNext(range.nextNode, range.nextNode.next)
                range.nextNode.next!!.casPrev(range.nextNode, range.nextNode.prev)
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val range = findRange(element)
        return range.nextNode.element == element
    }

    data class Range<E : Comparable<E>>(var currentNode: Node<E>, var nextNode: Node<E>)

    // returns when currentNode.element < element <= nextNode.element
    private fun findRange(element: E): Range<E> {
        while (true) {
            val range = Range(first, first.next!!)
            while (!range.currentNode.removed.value && (range.nextNode.element < element || range.nextNode.removed.value)) {
                if (range.nextNode.removed.value) {
                    val skipNode = range.nextNode.next!!.next
                    if (range.currentNode.casNext(range.nextNode, skipNode)) {
                        skipNode!!.casPrev(range.nextNode, range.currentNode)
                        range.nextNode = skipNode
                    } else {
                        range.nextNode = range.currentNode.next!!
                    }
                } else {
                    range.currentNode = range.nextNode
                    range.nextNode = range.currentNode.next!!
                }
            }
            if (!range.currentNode.removed.value)
                return range;
        }
    }
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