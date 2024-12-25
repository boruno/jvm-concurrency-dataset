//package mpp.linkedlistset

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.Double.Companion.NEGATIVE_INFINITY
import kotlin.Double.Companion.POSITIVE_INFINITY

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null)
    private val last = Node<E>(prev = first, element = null, next = null)
    init {
        first.setNext(last)
        first.setValue( NEGATIVE_INFINITY as E )
        last.setValue( POSITIVE_INFINITY as E )
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
        var node = first
        var prevUndel = first
        while ( node.next != null ) {
            if ( node.element < element && node.next!!.element >= element ) {
                if ( node.next!!.element == element ) { // и проверка, что не удалено
                    return false
                } else {
                    val newNode = Node<E>(prev = node, element = element, next = node.next)
                    if ( node.deleted.get() == false ) {
                        node.casNext(node.next, newNode)
                    } else {
                        prevUndel.casNext( prevUndel.next, newNode )
                    }
                    return true
                }
            }
            if ( node.deleted.get() == false ) {
                prevUndel = node
            }
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
            if ( ( node.element as Int ) < ( element as Int ) && ( node.next!!.element as Int ) >= ( element as Int ) ) {
                if ( node.next!!.element == element && node.next!!.deleted.get() == false ) { // и проверка, что не удалено
                    node.next!!.deleted.compareAndSet( false, true )
                    node.casNext( node.next, node.next!!.next )
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
            if ( node.element < element && node.next!!.element >= element ) {
                if ( node.next!!.element == element && node.next!!.deleted.get() == false ) { // и проверка, что не удалено
                    return true;
                }
                break;
            }
            node = node.next!!
        }
        return false;
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private var _element = element // `null` for the first and the last nodes
    val element get() = _element!!
    val deleted = AtomicReference<Boolean>(false)

    private val _prev = atomic(prev)
    val prev get() = _prev.value
    fun setPrev(value: Node<E>?) {
        _prev.value = value
    }

    fun setValue( element : E? ) {
        _element = element
    }


    fun casPrev(expected: Node<E>?, update: Node<E>?) =
        _prev.compareAndSet(expected, update)

    private val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }
    fun casNext(expected: Node<E>?, update: Node<E>?) : Boolean {
        if ( deleted.get() )
            return false
        return _next.compareAndSet(expected, update)
    }
}