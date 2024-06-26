package mpp.linkedlistset

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.Double.Companion.NEGATIVE_INFINITY
import kotlin.Double.Companion.POSITIVE_INFINITY

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = Undeleted<E>( -10000000 as E), next = null)
    private val last = Node<E>(prev = first, element = Undeleted<E>( +10000000 as E ), next = null)
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
        var node = head.value
        var prevUndel = node
        while ( node.next != null ) {
            if ( node.element.value!!.value!! < element && node.next!!.element.value!!.value!! >= element ) {
                if ( node.next!!.element.value!!.value == element && node.next!!.element.value !is Deleted<*>) {
                    return false
                } else {
                    val newNode = Node<E>(prev = node, element = Undeleted<E>(element), next = node.next)
                    if ( node.element.value!!.value!! > element) {
                        node = head.value
                        prevUndel = node
                    }
                    if ( node.element.value !is Deleted<*> )
                        prevUndel = node
                    if ( prevUndel.casNext( prevUndel.next, newNode ) ) {
                        return true
                    } else {
                        node = head.value
                        prevUndel = node
                        continue
                    }

                }
            }
            if ( node.element.value !is Deleted<*> )
                prevUndel = node
            node = node.next!!
        }
        return false
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: E): Boolean {
        var node = first
        while ( node.next != null ) {
            val fc = node.next!!.element
            if ( node.element.value!!.value!! < element && node.next!!.element.value!!.value!! >= element ) {
                if ( node.next!!.element.value!!.value!! == element && node.next!!.element.value !is Deleted<*> ) { // и проверка, что не удалено
                    if ( node.next!!.element.compareAndSet( fc.value, Deleted<E>(node.next!!.element.value!!.value) ) ) {
                        node.casNext( node.next, node.next!!.next )
                    }
                    return true
                }
                return false
            }
            node = node.next!!
        }
        return false
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var node = first
        while ( node.next != null ) {
            if ( node.element.value!!.value!! < element && node.next!!.element.value!!.value!! >= element ) {
                if ( node.next!!.element.value!!.value!! == element && ( node.next!!.element.value !is Deleted<E> ) ) { // и проверка, что не удалено
                    return true;
                }
            }
            if ( node.element.value!!.value!! > element) {
                break
            }
            node = node.next!!
        }
        return false;
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: Value<E>?, next: Node<E>?) {
    private val _element = atomic(element)
    val element get() = _element
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
    fun casNext(expected: Node<E>?, update: Node<E>?) : Boolean {
        if ( _element.value is Deleted<*> )
            return false
        return _next.compareAndSet(expected, update)
    }
}


abstract class Value<E> (value: E? ) {
    val value: E? = value
}

class Deleted<E>(value: E? ) : Value<E>( value ) {
}

class Undeleted<E>( value: E? ) : Value<E>( value ) {
}