//package mpp.linkedlistset

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
            if (l.casNext(r, newNode))
                break
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

    private class Node<E : Comparable<E>>(val element: E?, val next: Node<E>?) {
        private val atomicNext = atomic(next)
        val removed = atomic(false)

        fun casNext(expected: Node<E>?, update: Node<E>?) =
            atomicNext.compareAndSet(expected, update)

        fun tryRemove() = removed.compareAndSet(false, true)
    }

}