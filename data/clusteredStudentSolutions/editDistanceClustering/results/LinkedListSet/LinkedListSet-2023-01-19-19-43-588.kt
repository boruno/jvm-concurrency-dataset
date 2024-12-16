package mpp.linkedlistset

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicMarkableReference

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(element = null, next = null)
    private val last = Node<E>(element = null, next = null)
    init {
        first.next = AtomicMarkableReference(last, false)
    }

    private val head = AtomicMarkableReference(first, false)

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        val key = element.hashCode()
        while (true) {
            val window = find(head.reference, key)
            val pred = window.pred
            val curr = window.curr
            if (curr.key == key) {
                return false
            } else {
                val node = Node(element, curr)
                if (pred.next.compareAndSet(curr, node, false, false)) return true

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
        val key = element.hashCode()
        var snip: Boolean
        while (true) {
            val window = find(head.reference, key)
            val pred = window.pred
            val curr = window.curr
            return if (curr.key != key) {
                false
            } else {
                val succ = curr.next.reference
                snip = curr.next.attemptMark(succ, true)
                if (!snip) continue
                pred.next.compareAndSet(curr, succ, false, false)
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
        val key = element.hashCode()
        var curr = head
        while (curr.reference.key < key) {
            curr = curr.reference.next
            curr.reference.next.get(marked)
        }
        return curr.reference.key == key && !marked[0]
    }
}

private data class Window<E : Comparable<E>>(val pred: Node<E>, val curr: Node<E>)

private fun <E: Comparable<E>> find(head: Node<E>, key: Int): Window<E> {
    var pred: Node<E>?
    var curr: Node<E>?
    var succ: Node<E>?
    val marked = BooleanArray(1) { false }
    var snip: Boolean
    loop@ while (true) {
        pred = head
        curr = pred.next.reference
        while (true) {
            succ = curr!!.next.get(marked)
            while (marked[0]) {
                snip = pred!!.next.compareAndSet(curr, succ, false, false)
                if (!snip) continue@loop
                curr = succ
                succ = curr!!.next.get(marked)
            }
            if (curr!!.key >= key) return Window(pred!!, curr)
            pred = curr
            curr = succ
        }
    }
}

private class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!


    var next = AtomicMarkableReference(next, false)
    val key = element.hashCode()
    /*
    val next get() = _next.
    fun setNext(value: Node<E>?) {
        _next.value = value
    }
    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
     */
}