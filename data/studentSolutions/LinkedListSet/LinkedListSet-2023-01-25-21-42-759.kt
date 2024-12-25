@file:Suppress("UNCHECKED_CAST")
//package mpp.linkedlistset

import kotlinx.atomicfu.*

data class Removed<E>(val value: E)

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
            var curValue: Any? = first
            var nextValue: Any? = first.next

                if ((curValue as Node<E>).element != null || (nextValue as Node<E>).element != null) {
                    if (curValue.element == null && element <= (nextValue as Node<E>).element!!) {
                        curValue = nextValue
                        nextValue = nextValue.next
                    } else if ((nextValue as Node<E>).element != null) {
                        while (element <= (curValue as Node<E>).element!! && element > (nextValue as Node<E>).element!!) {
                            curValue = nextValue
                            nextValue = nextValue.next
                        }
                    }
                }

            val insertionNode = Node(null, element, nextValue)

            return (curValue as Node<E>).casNext(nextValue, insertionNode)
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
        var curValue: Any? = first
        var nextValue: Any? = first.next

        while (true) {
            if (nextValue == null) {
                return false
            }

            while (element != (nextValue as Node<*>).element) {
                curValue = (curValue as Node<*>).next
                nextValue = (nextValue as Node<*>).next
            }

            val removedValue = Removed((nextValue as Node<*>).element)

            if (curValue !is Removed<*>) {
                if ((curValue as Node<*>).casNext(removedValue, nextValue.next)) {
                    return true
                }
            } else {
                curValue = first
                nextValue = first.next
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var curValue: Node<E>? = first
        while (curValue?.element != element) {
            if (curValue == null) {
                return false
            }
            curValue = (curValue.next as Node<E>?)
        }
        return true
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Any?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element

//    private val _prev = atomic(prev)
//    val prev get() = _prev.value
//    fun setPrev(value: Node<E>?) {
//        _prev.value = value
//    }
//    fun casPrev(expected: Node<E>?, update: Node<E>?) =
//        _prev.compareAndSet(expected, update)

    private val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }
    fun casNext(expected: Any?, update: Any?) =
        _next.compareAndSet(expected, update)
}