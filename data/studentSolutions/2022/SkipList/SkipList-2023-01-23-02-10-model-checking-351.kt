package mpp.skiplist

/*
class SkipList<E : Comparable<E>> {

    */
/**
 * Adds the specified element to this set
 * if it is not already present.
 *
 * Returns `true` if this set did not
 * already contain the specified element.
 *//*

    fun add(element: E): Boolean {
        TODO("implement me")
    }

    */
/**
 * Removes the specified element from this set
 * if it is present.
 *
 * Returns `true` if this set contained
 * the specified element.
 *//*

    fun remove(element: E): Boolean {
        TODO("implement me")
    }

    */
/**
 * Returns `true` if this set contains
 * the specified element.
 *//*

    fun contains(element: E): Boolean {
        TODO("implement me")
    }
}*/

import java.util.concurrent.atomic.AtomicMarkableReference
import kotlin.contracts.contract
import kotlin.random.Random

class SkipList<E : Comparable<E>>{
    val head = Node<E>(Int.MIN_VALUE)
    val tail = Node<E>(Int.MAX_VALUE)
    /*init {
        for (i in head.next.indices) head.next[i] = AtomicMarkableReference(tail, false)
    }*/

    init {
        for (i in head.next.indices) head.next[i].compareAndSet(null, tail, false, false)
    }

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        val topLevel = randomLevel()
        val bottomLevel = 0
        val preds = arrayOfNulls<Node<E>>(MAX_LEVEL + 1)
        val succs = arrayOfNulls<Node<E>>(MAX_LEVEL + 1)
        while (true) {
            val found = find(element, preds, succs)
            if (found) {
                return false
            } else {
                val newNode = Node(element, topLevel);
                for (level in bottomLevel..topLevel) {
                    val succ = succs[level]
                    newNode.next[level].set(succ, false)
                }
                var pred = preds[bottomLevel]
                var succ = succs[bottomLevel]
                newNode.next[bottomLevel].set(succ, false)
                if (!pred!!.next[bottomLevel].compareAndSet(succ, newNode, false, false)) continue
                for (level in bottomLevel + 1..topLevel) {
                    while (true) {
                    pred = preds[level]
                    succ = succs[level]
                    if (pred!!.next[level].compareAndSet(succ, newNode, false, false))
                        break
                    find(element, preds, succs)
                    }
                }
                return true;
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
        val bottomLevel = 0
        val preds = arrayOfNulls<Node<E>>(MAX_LEVEL + 1)
        val succs = arrayOfNulls<Node<E>>(MAX_LEVEL + 1)
        var succ: Node<E>
        while (true) {
            val found = find(element, preds, succs)
            if (!found) {
                return false;
            } else {
                val nodeToRemove = succs[bottomLevel]
                for (level in nodeToRemove!!.topLevel downTo bottomLevel + 1) {
                    val marked = booleanArrayOf(false)
                    succ = nodeToRemove.next[level].get(marked)
                    while (!marked[0]) {
                        nodeToRemove.next[level].attemptMark(succ, true)
                        succ = nodeToRemove.next[level].get(marked)
                    }
                }
                val marked = booleanArrayOf(false)
                succ = nodeToRemove.next[bottomLevel].get(marked)
                while (true) {
                    val iMarkedIt =
                        nodeToRemove.next[bottomLevel].compareAndSet(succ, succ, false, true)
                    succ = succs[bottomLevel]!!.next[bottomLevel].get(marked)
                    if (iMarkedIt) {
                        find(element, preds, succs)
                        return true;
                    } else if (marked[0]) return false
                }
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val bottomLevel = 0;
        val v = element.hashCode();
        val marked = booleanArrayOf(false)
        var pred = head
        var curr: Node<E>? = null
        var succ: Node<E>
        for (level in MAX_LEVEL downTo bottomLevel) {
            curr = pred.next[level].reference;
            while (true) {
                succ = curr!!.next[level].get(marked);
                while (marked[0]) {
                    curr = pred.next[level].reference;
                    succ = curr.next[level].get(marked);
                }
                if (curr!!.key < v){
                    pred = curr
                    curr = succ
                } else {
                    break
                }
            }
        }
        return (curr!!.key == v);
    }

    private fun find(element: E, preds: Array<Node<E>?>, succs: Array<Node<E>?>): Boolean {
        val bottomLevel = 0
        val key = element.hashCode()
        val marked: BooleanArray = booleanArrayOf(false)
        var snip: Boolean
        var pred: Node<E>
        var curr: Node<E>? = null
        var succ:Node<E>?
        retry@
        while (true) {
            pred = head;
            for (level in MAX_LEVEL downTo bottomLevel) {
                curr = pred.next[level].reference;
                while (true) {
                    succ = curr!!.next[level].get(marked);
                while (marked[0]) {
                    snip = pred.next[level].compareAndSet(curr, succ, false, false)
                    if (!snip) continue@retry
                    curr = pred.next[level].reference;
                    succ = curr.next[level].get(marked);
                }
                if (curr!!.key < key) {
                    pred = curr; curr = succ
                } else {
                    break
                }
            }
                preds [level] = pred
                succs [level] = curr;
            }
            return (curr!!.key == key)
        }
    }
}

class Node<T> {
    val element: T?
    val key: Int
    val next: Array<AtomicMarkableReference<Node<T>>>
    val topLevel: Int
    constructor(key: Int) {
        element = null
        this.key = key
        next = Array(MAX_LEVEL + 1) { AtomicMarkableReference<Node<T>>(null, false) }
        topLevel = MAX_LEVEL
    }
    constructor (element: T, height: Int) {
        this.element = element
        key = element.hashCode()
        next = Array(height + 1) { AtomicMarkableReference<Node<T>>(null, false) }
        topLevel = height;
    }
}

private class Window<E>() {
    var predecessors = Array<Node<E>?>(MAX_LEVEL + 1) { null }
    var successors = Array<Node<E>?>(MAX_LEVEL + 1) { null }

    var found: Boolean = false
}

private fun randomLevel(): Int = Random.nextInt(MAX_LEVEL)

private const val MAX_LEVEL = 30
