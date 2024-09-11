package mpp.linkedlistset

import kotlinx.atomicfu.atomic

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
        var currNode: Node<E> = head.value
        while (true) {
            //println("ADD($currNode)")
            val currNext = currNode.next
            if (currNode === first && currNext === last) {
                val newNode = Node(currNode, element, currNode.next)
                if (currNode.casNext(currNext, newNode)) {
                    currNext.casPrev(currNode, newNode)
                    return true
                } else {
                    currNode = head.value
                    continue
                }
            } else if (currNode === first) {
                currNode = currNode.next!!
                continue
            } else if (currNode === last) break

            val currentValue = currNode.element
            if (currentValue < element) {
                currNode = currNode.next!!
            } else if (currentValue == element) return false
            else {
                val newNode = Node(currNode, element, currNode.next)
                if (currNode.casNext(currNext, newNode)) {
                    currNext!!.casPrev(currNode, newNode)
                    return true
                } else {
                    currNode = head.value
                }
            }

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
        var currNode: Node<E> = head.value
        while (true) {
            println("ADD($currNode)")
            val currNodeValue = currNode.elementNullable
            val nextNode = currNode.next!!
            if (nextNode != last) {
                val nextValue = nextNode.element
                if (element == nextValue) {
                    if (currNode.casNext(nextNode, nextNode.next)) return true
                } else if (element > nextValue) return false
            } else break
            currNode = currNode.next!!
        }
        return false
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var currNode: Node<E> = head.value
        while (true) {
            println("ADD($currNode)")
            val currNodeValue = currNode.elementNullable
            if (currNodeValue != null) {
                val curValue = currNodeValue
                if (element == currNode.element) return true
                if (element > currNode.element) return false
            }
            if (currNode.next == null) break
            currNode = currNode.next!!
        }
        return false
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!
    val elementNullable get() = _element

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