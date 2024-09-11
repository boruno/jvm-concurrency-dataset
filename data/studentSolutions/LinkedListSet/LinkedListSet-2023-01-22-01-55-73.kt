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
        val op = AddOp(first, last, element)
        op.complete()
        return op.result.value!!
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: E): Boolean {
        val op = RemoveOp(first, last, element)
        op.complete()
        return op.result.value!!
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val op = ContainsOp(first, last, element)
        op.complete()
        return op.result.value!!
    }


}

interface Op<E> {
    fun complete()
}

class DeleteOp<E : Comparable<E>> (val prev: Node<E>, val del: Node<E>, val next: Node<E>) : Op<E> {
    val result: AtomicRef<Boolean?> = atomic(null)

    override fun complete() {
        if (result.value == null) {
            prev.setOp(this)
            next.setOp(this)
        }
        next.casPrev(del, prev)
        prev.casNext(del, next)
        result.value = true
        next.freeOp(this)
        prev.freeOp(this)
    }

}

class RemoveOp<E : Comparable<E>> (first: Node<E>, val last: Node<E>, val element: E) : Op<E> {
    val node = atomic(first)
    val result: AtomicRef<Boolean?> = atomic(null)

    init {
        node.value.setOp(this)
    }

    override fun complete() {
        while (true) {
            if (result.value != null) {
                break
            }
            val curNode = node.value
            val nextNode = curNode.next!!
            if (nextNode.element == element) {
                curNode.op.compareAndSet(this, DeleteOp(curNode, nextNode, nextNode.next!!))
                result.compareAndSet(null, true)
                break
            }
            if (nextNode == last) {
                result.compareAndSet(null, false)
                break
            }
            nextNode.setOp(this)
            node.compareAndSet(curNode, nextNode)
            curNode.freeOp(this)
        }
        node.value.freeOp(this)
    }
}

class AddNewOp<E : Comparable<E>> (val prev: Node<E>, val next: Node<E>, val element: E) : Op<E> {
    val result: AtomicRef<Boolean?> = atomic(null)

    override fun complete() {
        if (result.value == null) {
            prev.setOp(this)
            next.setOp(this)
        }
        val newNode = Node(prev, element, next)
        next.casPrev(prev, newNode)
        prev.casNext(next, newNode)
        result.value = true
        next.freeOp(this)
        prev.freeOp(this)
    }
}

class AddOp<E : Comparable<E>> (first: Node<E>, val last: Node<E>, val element: E) : Op<E> {
    val node = atomic(first)
    val result: AtomicRef<Boolean?> = atomic(null)

    init {
        node.value.setOp(this)
    }

    override fun complete() {
        while (true) {
            if (result.value != null) {
                break
            }
            val curNode = node.value
            if (curNode.element == element) {
                result.compareAndSet(null, false)
                break
            }
            val nextNode = curNode.next!!
            if (nextNode == last) {
                curNode.op.compareAndSet(this, AddNewOp(curNode, nextNode, element))
                result.compareAndSet(null, true)
                break
            }
            nextNode.setOp(this)
            node.compareAndSet(curNode, nextNode)
            curNode.freeOp(this)
        }
        node.value.freeOp(this)
    }
}

class ContainsOp<E : Comparable<E>> (first: Node<E>, val last: Node<E>, val element: E) : Op<E> {
    val node = atomic(first)
    val result: AtomicRef<Boolean?> = atomic(null)

    init {
        node.value.setOp(this)
    }

    override fun complete() {
        while (true) {
            if (result.value != null) {
                break
            }
            val curNode = node.value
            if (curNode.element == element) {
                result.compareAndSet(null, true)
                break
            }
            val nextNode = curNode.next!!
            if (nextNode == last) {
                result.compareAndSet(null, false)
                break
            }
            nextNode.setOp(this)
            node.compareAndSet(curNode, nextNode)
            curNode.freeOp(this)
        }
        node.value.freeOp(this)
    }
}

class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element
    val op : AtomicRef<Op<E>?> = atomic(null)

    fun setOp(newOp : Op<E>) {
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
    }

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