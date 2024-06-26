@file:Suppress("UNCHECKED_CAST")
package mpp.linkedlistset

import kotlinx.atomicfu.*

data class Removed(val value: Node<*>)

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
            var curValue: Any? = head.value
            var nextValue: Any? = head.value.next

            if ((curValue as Node<E>).element != null || (nextValue as Node<E>).element != null) {
                if (nextValue == null) return false

                if ((curValue as Node<E>).element == null) {
                    curValue = nextValue
                    nextValue = (nextValue as Node<E>).next
                }

                if (nextValue != last) {
                    while (element >= (nextValue as Node<E>).element!!) {
                        curValue = nextValue
                        nextValue = nextValue.next

                        if (nextValue == last) break
                    }
                }
            }

            val insertionNode = Node(null, element, nextValue)

            if ((curValue as Node<E>).casNext(nextValue, insertionNode)) {
                return true
            } else {
                continue
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
        var curValue: Any? = head.value
        var nextValue: Any? = head.value.next
        var nextValueElement: E? = null

        while (true) {
            if (nextValue == null || nextValue == last) {
                return false
            }

            if (nextValue !is Removed) {
                while ((nextValue as Node<*>).element != element) {
                    if (nextValue == last) return false
                    curValue = (curValue as Node<*>).next
                    nextValue = (nextValue as Node<*>).next
                }

                val removedValue = Removed(nextValue)
                (curValue as Node<E>).setNext(removedValue)

                if (curValue !is Removed) {
                    if ((curValue as Node<*>).casNext(removedValue, nextValue.next)) {
                        return true
                    }
                } else {
                    curValue = first
                    nextValue = first.next
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

class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Any?) {
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
    fun setNext(value: Any?) {
        _next.value = value
    }
    fun casNext(expected: Any?, update: Any?) =
        _next.compareAndSet(expected, update)
}