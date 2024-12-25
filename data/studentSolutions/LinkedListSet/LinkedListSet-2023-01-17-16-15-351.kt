//package mpp.linkedlistset

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicMarkableReference
import java.util.concurrent.atomic.AtomicReference

class LinkedListSet<E : Comparable<E>> {
    private val last = Node<E>(element = null, next = null, false)
    private val first = Node<E>(element = null, next = last, false)


    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        while (true) {
            val (prev, cur) = find(first, element)
            if (cur.element == element) {
                return false
            } else {
                val node = Node(element, cur, false)
                if (prev.casNext(cur, node, flag1 = false, flag2 = false)) {
                    return true
                }
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
            val (prev, cur) = find(first, element)
            return if (cur.element != element) {
                false
            } else {
                val s = cur.next!!
                if (!s.attemptNext(s, true)) {
                    continue
                }
                prev.casNext(cur, s, flag1 = false, flag2 = false)
                true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val marked = BooleanArray(1) { false }
        var cur = first.next!!
        while (!cur.isFirstOrLast() && cur.element < element) {
            cur = cur.next!!
            cur.getNext(marked)
        }
        return cur.element == element && !marked[0]
    }


    private fun find(head: Node<E>, key: E): Pair<Node<E>, Node<E>> {
        var pred: Node<E>
        var curr: Node<E>
        val marked = BooleanArray(1) { false }
        retry@ while (true) {
            pred = head
            curr = pred.next!!
            while (true) {
                var s = curr.getNext(marked)
                while (marked[0]) {
                    if (!pred.casNext(curr, s, flag1 = false, flag2 = false)) {
                        continue@retry
                    }
                    curr = s!!
                    s = curr.getNext(marked)
                }
                if (curr.isFirstOrLast() || curr.element >= key) {
                    return pred to curr
                }
                pred = curr
                curr = s!!
            }
        }
    }
}

private class Node<E : Comparable<E>>(element: E?, next: Node<E>?, flag: Boolean) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    fun isFirstOrLast() = _element == null

    private val _next = AtomicMarkableReference(next, flag)

    val next get() = _next.reference

    fun attemptNext(x: Node<E>, flag: Boolean) = _next.attemptMark(x, flag)
    fun getNext(marked: BooleanArray) = _next.get(marked)
    fun setNext(value: Node<E>?, flag: Boolean) {
        _next.set(value, flag)
    }

    fun casNext(expected: Node<E>?, update: Node<E>?, flag1: Boolean, flag2: Boolean) =
        _next.compareAndSet(expected, update, flag1, flag2)
}


