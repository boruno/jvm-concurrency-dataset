package mpp.skiplist

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class SkipList<E : Comparable<E>> {
    private val head = Valid(Int.MIN_VALUE as E, MAX_LEVEL)
    private val tail = Valid(Int.MAX_VALUE as E, MAX_LEVEL)


    init {
        for (i in 0 until MAX_LEVEL) {
            head.next[i].value = tail
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
        val topLevel = randomLevel()
        val bottomLevel = 0


        val preds = arrayOfNulls<Node<E>>(MAX_LEVEL+1)
        val succs = arrayOfNulls<Node<E>>(MAX_LEVEL+1)
        while(true) {
            val found = find(element, preds, succs)
            if (found) {
                return false
            } else {
                val newNode = Valid(element, topLevel)
                for (level in bottomLevel..topLevel) {
                    val succ = succs[level]
                    newNode.next[level].value = succ
                }
                var pred = preds[bottomLevel]
                var succ = succs[bottomLevel]
                if (pred !is Removed && !(pred as Valid).next[bottomLevel].compareAndSet(succ, newNode)) {
                    continue
                }
                for (level in bottomLevel + 1..topLevel) {
                    while (true) {
                        pred = preds[level]
                        succ = succs[level]
                        if (pred !is Removed && (pred as Valid).next[level].compareAndSet(succ, newNode)) {
                            break
                        }
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


        val preds = arrayOfNulls<Node<E>>(MAX_LEVEL+1)
        val succs = arrayOfNulls<Node<E>>(MAX_LEVEL+1)
        while(true) {
            val found = find(element, preds, succs)
            if (!found) {
                return false
            } else {
                val nodeToRemove = succs[bottomLevel] as Valid
                for (level in nodeToRemove.level downTo bottomLevel + 1) {
//                    try to tag as Removed
                    while (true) {
                        val succ = nodeToRemove.next[level].value!!
                        if (succ is Valid) {
                            if (nodeToRemove.next[level].compareAndSet(succ, Removed(succ))) {
                                break
                            }
                        } else {
                            break
                        }
                    }
                }
                val succ = nodeToRemove.next[bottomLevel].value!!

                return if (succ is Valid && nodeToRemove.next[bottomLevel].compareAndSet(succ, Removed(succ))) {
                    find(element, preds, succs)
                    true
                } else false

            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val bottomLevel = 0

        var pred : Node<E>? = head
        var curr : Node<E>? = null
        var succ : Node<E>? = null

        for (level in MAX_LEVEL downTo bottomLevel) {
            curr = (pred as Valid).next[level].value!!
            while (true) {
                succ = (curr as Valid).next[level].value
                while(succ is Removed) {
                    curr = (succ ).internalNode
                    succ = (curr ).next[level].value
                }
                if ((curr as Valid).element < element) return true
                if ((curr as Valid).element < element) {
                    pred = curr
                    curr = succ
                } else {
                    break
                }
            }
        }
        return (curr as Valid).element == element
    }

    private fun randomLevel(): Int = Random.nextInt(MAX_LEVEL)

    private fun find(element: E, preds: Array<Node<E>?>, succs:Array<Node<E>?>): Boolean {
        var pred : Node<E>? = null
        var curr : Node<E>? = null
        var succ : Node<E>? = null
        retry@
        while (true) {
            pred = head
            for (level in MAX_LEVEL downTo 0) {
                curr = (pred as Valid).next[level].value as Valid
                while (true) {
//                    if marked Removed –> help to remove
                    succ = (curr as Valid).next[level].value
                    while (succ is Removed) {
                        val succNode = succ.internalNode
                        if (!(pred as Valid).next[level].compareAndSet(curr, succNode)) {
//                            full retry
                            continue@retry
                        }
                        curr = pred.next[level].value as Valid
                        succ = curr.next[level].value
                    }
                    if ((curr as Valid).element < element) {
                        pred = curr
                        curr = succ
                    } else {
                        break
                    }
                }
                preds[level] = pred
                succs[level] = curr
            }
            return (curr as Valid).element == element

        }
    }
}
private interface Node<E>

private class Valid<E>(val element: E, val level: Int) : Node<E> {
    val next = atomicArrayOfNulls<Node<E>?>(level+1)
}

private class Removed<E>(val internalNode: Valid<E>) : Node<E>


const val MAX_LEVEL = 32