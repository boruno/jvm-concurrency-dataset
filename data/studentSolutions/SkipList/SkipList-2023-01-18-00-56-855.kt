//package mpp.skiplist

import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.atomicArrayOfNulls
import java.lang.ref.Reference
import java.util.concurrent.atomic.AtomicMarkableReference
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

class SkipList<E : Comparable<E>> {
    private val head = Node<E>(Integer.MIN_VALUE);
    private val tail = Node<E>(Integer.MAX_VALUE);
    init {
        for (i in 0 until head.next!!.size) {
            head.next!![i].set(tail, false)
        }
    }
        /**
         * Adds the specified element to this set
         * if it is not already present.
         *
         * Returns `true` if this set did not
         * already contain the specified element.
         */
        fun add(element: E): Boolean {
            val topLevel = Random(1).nextInt(0, MAX_LEVEL)
            val bottomLevel = 0
            val preds = Array<Node<E>?>(MAX_LEVEL + 1) {null}
            val succs = Array<Node<E>?>(MAX_LEVEL + 1) {null}
            while(true) {
                val found = find(element, preds, succs)
                if (found) {
                    return false
                } else {
                    val newNode = Node(element, topLevel)
                    for (level in bottomLevel until topLevel)
                    {
                        val succ = succs[level]
                        newNode.next!![level].set(succ, false)
                    }
                    var pred = preds[bottomLevel]
                    var succ = succs[bottomLevel]
                    newNode.next!![bottomLevel].set(succ, false)
                    if (!pred!!.next!![bottomLevel].compareAndSet(succ, newNode, false, false)) {
                        continue
                    }
                    for (level in bottomLevel + 1 until topLevel) {
                        while(true) {
                            pred = preds[level]
                            succ = succs[level]
                            if (pred!!.next!![level].compareAndSet(succ, newNode, false, false))
                                break
                            find(element, preds, succs)
                        }
                    }
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
            val bottomLevel = 0
            val preds = Array<Node<E>?>(MAX_LEVEL + 1) {null}
            val succs = Array<Node<E>?>(MAX_LEVEL + 1) {null}
            var succ: Node<E>? = null
            while(true) {
                val found = find(element, preds, succs)
                if (!found) {
                    return false
                }
                else {
                    val nodeToRemove = succs[bottomLevel]
                    for (level in nodeToRemove!!.topLevel downTo bottomLevel+1)
                    {
                        val marked = BooleanArray(1) {false}
                        succ = nodeToRemove.next!![level].get(marked)
                        while(!marked[0]) {
                            nodeToRemove.next!![level].attemptMark(succ, true)
                            succ = nodeToRemove.next!![level].get(marked)
                        }
                    }
                    val marked = BooleanArray(1) {false}
                    succ = nodeToRemove.next!![bottomLevel].get(marked)
                    while(true) {
                        val iMarkedIt = nodeToRemove.next!![bottomLevel].compareAndSet(succ, succ, false, true)
                        succ = succs[bottomLevel]!!.next!![bottomLevel].get(marked)
                        if (iMarkedIt) {
                            find(element, preds, succs)
                            return true
                        }
                        else if (marked[0]) return false
                    }
                }
            }
        }

        /**
         * Returns `true` if this set contains
         * the specified element.
         */
        fun contains(element: E): Boolean {
            val bottomLevel = 0
            val v = element.hashCode()
            var marked = BooleanArray(1) {false}
            var pred: Node<E> = head
            var curr: Node<E>? = null
            var succ: Node<E>? = null
            for (level in MAX_LEVEL downTo bottomLevel) {
                curr = pred.next!![level].reference
                while(true)
                {
                    succ = curr!!.next!![level].reference
                    while(marked[0]) {
                        curr = pred.next!![level].reference
                        succ = curr!!.next!![level].get(marked)
                    }
                    if (curr!!.key < v) {
                        pred = curr
                        curr = succ
                    }
                    else
                    {
                        break
                    }
                }
            }
            return (curr!!.key == v)
        }

        fun find(x: E, preds: Array<Node<E>?>, succs: Array<Node<E>?>): Boolean {
            val bottomLevel = 0
            val key = x.hashCode()
            val marked = BooleanArray(1) {false}
            var snip: Boolean = false
            var pred: Node<E>
            var curr: Node<E>? = null
            var succ: Node<E>?
            loop@ while(true){
                    pred = head
                    for (level in MAX_LEVEL downTo bottomLevel) {
                        curr = pred.next!![level].reference!!
                        while(true)
                        {
                            succ = curr!!.next!![level].get(marked)
                            if (succ == null)
                            {
                                preds[level] = pred
                                succs[level] = tail
                                break
                            }
                            while(marked[0])
                            {
                                snip = pred.next!![level].compareAndSet(curr, succ, false, false)
                                if (!snip) continue@loop
                                curr = pred.next!![level].reference!!
                                succ = curr.next!![level].get(marked)!!
                            }
                            if (curr!!.key < key)
                            {
                                pred = curr
                                curr = succ
                            }
                            else
                            {
                                break
                            }
                        }
                        preds[level] = pred
                        succs[level] = curr
                    }
                return (curr!!.key == key)
                }
        }
    }

    public class Node<T>() {
        var value: T? = null
        var key: Int = -1
        var next: Array<AtomicMarkableReference<Node<T>?>>? = null
        var topLevel: Int = -1

        constructor(nodeKey: Int) : this() {
            key = nodeKey
            value = null
            topLevel = MAX_LEVEL
            next = Array(MAX_LEVEL + 1) {AtomicMarkableReference(null, false)}
        }

        constructor(x: T, height: Int) : this() {
            value = x
            key = x.hashCode()
            next = Array(height + 1) {AtomicMarkableReference(null, false)}
            topLevel = height
        }
    }

private const val MAX_LEVEL = 8;
