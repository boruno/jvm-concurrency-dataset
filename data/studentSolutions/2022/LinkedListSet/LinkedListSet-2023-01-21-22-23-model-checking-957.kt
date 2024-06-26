package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val head = Node<E>(element = null, next = MarkedNext(Node(null, MarkedNext(null))))

    private fun addContains(element: E, isAdd: Boolean): Boolean {
        var prev = head
        var prevNext = prev.mNext.value
        var cur = prevNext.next!!
        while (true) {
            val curNext = cur.mNext.value
            if (curNext.isRemoved) {
                val newPrevNext = MarkedNext(curNext.next)
                if (prev.mNext.compareAndSet(prevNext, newPrevNext)) {
                    prevNext = newPrevNext
                    continue
                } else { return addContains(element, isAdd) }
            }
            if (cur.element != null && cur.element !! == element) { return !isAdd }
            if (cur.element == null || cur.element !!> element) {
                if (!isAdd) { return false }
                val newNode = Node(element, MarkedNext(curNext.next))
                if (prev.mNext.compareAndSet(prevNext, MarkedNext(newNode))) { return true }
                return addContains(element, isAdd)
            }
            prev = cur
            prevNext = curNext
            cur = prevNext.next!!
        }
    }

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean = addContains(element, true)

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: E): Boolean {
        TODO("implement me")
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean = addContains(element, false)
}

private class MarkedNext<E : Comparable<E>>(val next: Node<E>?) {
    val isRemoved = false
}

private class Node<E : Comparable<E>>(val element: E?, next: MarkedNext<E>) {
    val mNext = atomic(next)
}