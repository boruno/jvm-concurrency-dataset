//package mpp.linkedlistset

import kotlinx.atomicfu.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

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
            var cur = head.value.getNext()!!
            while (cur.getNext() != null && (cur.element.isEmpty || cur.element.get() < element)) {
                cur = cur.getNext()!!
            }
            if (cur != first && cur != last && cur.element.get() == element) {
                return false
            }

            val prev = cur.getPrev()!!
            val my = Node(prev, Optional.ofNullable(element), cur)

            if (cas1or2(true, prev, cur, my, cur, prev, my)) {
                return true
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
            var cur = head.value.getNext()!!
            while (cur.getNext() != null && (cur.element.isEmpty || cur.element.get() < element)) {
                cur = cur.getNext()!!
            }
            if (cur == first || cur == last || cur.element.isEmpty || cur.element.get() != element) {
                return false
            }

            val prev = cur.getPrev()!!
            val next = cur.getNext()!!

            if (cas1or2(true, prev, cur, next, next, cur, prev)) {
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var cur = head.value.getNext()!!
        while (cur.getNext() != null && (cur.element.isEmpty || cur.element.get() < element)) {
            cur = cur.getNext()!!
        }
        return cur != first && cur != last && !cur.element.isEmpty && cur.element.get() == element
    }

    private fun cas1or2(is2: Boolean,
                        element1: Node<E>, expected1: Any?, update1: Any?,
                        element2: Node<E>, expected2: Any?, update2: Any?): Boolean {
        var v1 = element1.next
//        println("v1: $v1")
        if (v1 is Descriptor<*> && (v1.st.compareAndSet(Status.INITIAL.ordinal, Status.FAILED.ordinal) ||
                    v1.st.get() == Status.FAILED.ordinal)) {
            element1.casNext(v1, v1.cur!!)
            return false
        }

        var v2 = element2.prev
//        println("v2: $v2")
        if (v2 is Descriptor<*> && (v2.st.compareAndSet(Status.INITIAL.ordinal, Status.FAILED.ordinal) ||
                    v2.st.get() == Status.FAILED.ordinal)) {
            element2.casPrev(v2, v2.cur!!)
            return false
        }

        val st = AtomicInteger(Status.INITIAL.ordinal)
        val res: Boolean

        if (v1 !is Descriptor<*> && v2 !is Descriptor<*>) {
            v1 = Descriptor(expected1, update1, st)
            v2 = Descriptor(expected2, update2, st)
            res = element1.casNext(expected1, v1)
                    && (element2.casPrev(expected2, v2))
                    && st.compareAndSet(Status.INITIAL.ordinal, Status.SUCCESS.ordinal)
            if (!res) {
                st.compareAndSet(Status.INITIAL.ordinal, Status.FAILED.ordinal)
                return false
            }
        } else {
            res = false
        }

        if (v1 is Descriptor<*> && v1.st.get() == Status.SUCCESS.ordinal) {
            element1.casNext(v1, v1.upd!!)
        }

        if (v2 is Descriptor<*> && v2.st.get() == Status.SUCCESS.ordinal) {
            element2.casPrev(v2, v2.upd!!)
        }

        return res
    }
}

@Suppress("UNCHECKED_CAST")
private class Node<E : Comparable<E>>(prev: Any?, element: Optional<E>?, next: Any?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    val _prev = atomic(prev)
    val prev get() = _prev.value

    fun getPrev(): Node<E>? {
        val pr = _prev.value
        return when (pr) {
            is Descriptor<*> -> pr.get() as Node<E>?
            else -> pr as Node<E>?
        }
    }

    fun setPrev(value: Any?) {
        _prev.value = value
    }
    fun casPrev(expected: Any?, update: Any?) =
        _prev.compareAndSet(expected, update)

    val _next = atomic(next)
    val next get() = _next.value

    fun getNext(): Node<E>? {
        val pr = _next.value
        return when (pr) {
            is Descriptor<*> -> pr.get() as Node<E>?
            else -> pr as Node<E>?
        }
    }

    fun setNext(value: Any?) {
        _next.value = value
    }
    fun casNext(expected: Any?, update: Any?) =
        _next.compareAndSet(expected, update)
}

enum class Status {
    INITIAL, FAILED, SUCCESS
}

class Descriptor<E>(val cur: E, val upd: E, val st: AtomicInteger) {

    fun get(): E {
        return when (st.get()) {
            Status.SUCCESS.ordinal -> upd
            else -> cur
        }
    }

    override fun toString(): String {
        return "Descriptor(cur=$cur, upd=$upd, st=$st)"
    }
}