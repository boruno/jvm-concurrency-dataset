package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = Int.MIN_VALUE as E, next = null)
    private val last = Node<E>(prev = first, element = Int.MAX_VALUE as E, next = null)
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
        var curHead = head.value
        var curNext = curHead.next
        while (true) {
            if (curNext != null) {
                if (curHead.element < element && element < curNext.element) {
                    val newNode = Node(curHead, element, curNext)
                    if (curHead.casNext(curNext, newNode)) {
                        return true
                    }
                }
                curHead = curHead.next!!
                curNext = curHead.next
            } else {
                return false
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
        var curHead = head.value
        var curNext = curHead.next
        while (true) {
            if (curNext != null) {
                if (curNext is Removed<*>) {
                    curNext = curNext.next
                    continue
                }
                if (curNext.element == element ) {
                    val newNode = Removed(curNext)
                    if (curHead.casNext(curNext, newNode)) {
                        if (curHead.next!!.casPrev(curNext, curHead)) {
                            return true
                        }
                    }
                }
                curHead = curHead.next!!
                curNext = curHead.next
            } else {
                return false
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var curHead = head.value
        while (true) {
            if (curHead.next != null) {
                if (curHead is Removed<*>) {
                    continue
                }
                if (curHead.element == element) {
                    return true
                }
                curHead = curHead.next!!
            } else {
                return false
            }
        }
    }
}

abstract class Element(){

}

private open class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
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

private class Removed<E : Comparable<E>>(node: Node<E>) : Node<E>(node.prev, node.element, node.next) {
}