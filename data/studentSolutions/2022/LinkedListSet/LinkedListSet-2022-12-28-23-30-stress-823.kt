package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val last = Node<E>(element = null, next = null)
    private val first = Node<E>(element = null, next = last)

    //private val head = atomic(first)

    private fun search(element: E): Node<E> {
        var l = first        // l.element < @element
        var r = first.next!! // r.element >= @element
        while (r.next != null) {
            if (r.element!! < element) {
                l = r
                r = r.next!!
            } else {
                break
            }
        }
        return l
    }


    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        while (true) {
            val l = search(element)
            val r = l.next!!
            val rValue = r.element
            if (rValue != null && rValue == element)
                return false
            val newNode = Node(element, r)
            while (!l.casNext(r, newNode)) {}
            break
        }
        return true
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
            val l = search(element)
            val r = l.next!!
            val rValue = r.element // should equal to @element
            if (rValue == null || rValue != element)
                return false
            if (!r.tryRemove())
                continue
            if (!l.casNext(r, r.next)) {
                r.removed.value = false
                continue
            }
            if (l.removed.value) {
                r.removed.value = false
            } else {
                break
            }
            break
        }
        return true
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val l = search(element)
        val r = l.next!!
        val rValue = r.element
        return rValue != null && rValue == element
    }
}

private class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    private val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }
    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)

    val removed = atomic(false)
    fun tryRemove() = removed.compareAndSet(false, true)
}
