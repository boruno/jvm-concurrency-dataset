package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    val first = Node<E>(prev = null, element = null, next = null)
    val last = Node<E>(prev = first, element = null, next = null)
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
        TODO()
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: E): Boolean {
        TODO()
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        TODO()
    }

    fun find(element: E): Node<E> {
        var curNode = first
        while (true) {
            curNode = curNode.next!!
            if (curNode == last) {
                return last
            }
            if (curNode.element!!.compareTo(element) == 0) {
                
            }
        }
    }
}

class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val removed = atomic(false)
    val element get() = _element
    //val op : AtomicRef<Op<E>?> = atomic(null)

    /*fun setOp(newOp : Op<E>) {
        while (true) {
            if (op.compareAndSet(null, newOp) || op.compareAndSet(newOp, newOp)) break

            val curOp = op.value
            if (curOp != null && curOp != newOp) {
                curOp.complete()
            }
        }
    }

    fun freeOp(curOp : Op<E>) {
        this.op.compareAndSet(curOp, null)
    }*/

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