package mpp.skiplist

import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class SkipList<E : Comparable<E>> {
    private val maxLevelCount: Int = 1
    private val bottomLevel = 0

    private val head = Node<E>(element = null, levelsCount = maxLevelCount)
    private val tail = Node<E>(element = null, levelsCount = maxLevelCount)
    init {
        for (level in 0 until maxLevelCount)
            head.next[level].getAndSet(tail)
    }

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        val maxNodeLevel: Int = ThreadLocalRandom.current().nextInt(1, maxLevelCount + 1)

        while (true) {
            if (contains(element))
                return false

            val newNode = Node(element, maxNodeLevel)
            var subsets = findSubsetsForElement(element)

            // Setting the 'next' node for new node on all levels.
            // Visible for one thread only.
            for (level in bottomLevel until maxNodeLevel) {
                val levelSubset = subsets[level]!!
                val newNodeNext = levelSubset.next ?: levelSubset.curr!!

                newNode.next[level].value = newNodeNext
            }

            val bottomSubset = subsets[bottomLevel]!!

            // If unsuccessful - something has changed in Skip-List structure, so try again from the start.
            /*val bottomPreviousNode = if (bottomSubset.next != null) bottomSubset.curr!!
                                     else bottomSubset.prev!!*/
            val bottomPreviousNode = if (bottomSubset.curr!!.element == null) {
                bottomSubset.prev
            } else if (bottomSubset.curr.element == element) {
                return false
            } else if (bottomSubset.curr.element!! > element) {
                bottomSubset.prev
            } else {
                bottomSubset.curr
            }

            val bottomNewNextNode = if (bottomPreviousNode == bottomSubset.prev) bottomSubset.curr else bottomSubset.next

            if (!bottomPreviousNode!!.next[bottomLevel].compareAndSet(bottomNewNextNode, newNode))
                continue

            // Reconnecting higher-level nodes.
            for (level in bottomLevel + 1 until maxNodeLevel) {
                while (true) {
                    val levelSubset = subsets[level]!!

                    // Finish iteration for current level if successful CAS.
                    val levelPreviousNode = if (levelSubset.next != null) levelSubset.curr!!
                                            else levelSubset.prev!!

                    val levelNodeNext = levelSubset.next ?: levelSubset.curr!!

                    if (levelPreviousNode.next[level].compareAndSet(levelNodeNext, newNode))
                        break

                    subsets = findSubsetsForElement(element)
                }
            }

            return true
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
            if (!contains(element))
                return false

            var subsets = findSubsetsForElement(element)

            val removingNodeSubset = subsets[bottomLevel]!!
            val removingNode = removingNodeSubset.curr!!
            val deletedNode = DeletedNode(removingNode.element, removingNode.levelsCount)

            for (level in removingNode.levelsCount - 1 downTo  bottomLevel + 1) {
                val levelSubset = subsets[level]!!

                while (true) {
                    val prevNode = levelSubset.prev!!

                    if (prevNode.next[level].compareAndSet(removingNode, deletedNode)) {
                        prevNode.next[level].compareAndSet(deletedNode, levelSubset.next)
                        break
                    }
                }
            }

            val bottomSubset = subsets[bottomLevel]

            while (true) {
                // If unsuccessful - something has changed in Skip-List structure, so try again from the start.
                val bottomPreviousNode = bottomSubset!!.prev!!

                if (bottomPreviousNode.next[bottomLevel].compareAndSet(removingNode, deletedNode)) {
                    bottomPreviousNode.next[bottomLevel].compareAndSet(deletedNode, bottomSubset.next)
                    return true
                }
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val subsets = findSubsetsForElement(element)
        return isElementPresentedInSubsets(element, subsets)
    }

    private fun findSubsetsForElement(element: E): Array<NodeSubset<E>?> {
        // Our aim is to define 'windows' (subsets) for target element on each level.
        val subsets: Array<NodeSubset<E>?> = arrayOfNulls(maxLevelCount)

        for (level in maxLevelCount - 1 downTo bottomLevel) {
            val subsetOnLevel = findSubsetOnLevel(element, level)
            subsets[level] = subsetOnLevel
        }

        return subsets
    }

    private fun findSubsetOnLevel(element: E, level: Int): NodeSubset<E> {
        var prevNode = head
        check(prevNode.levelsCount >= level) { println("Levels count: ${prevNode.levelsCount}; Level: $level") }
        val curNodeAtomic = prevNode.next[level]
        var curNode = curNodeAtomic.value!!

        while (curNode.element != null && element < curNode.element!!) {
            val nextNode = curNode.next[level].value ?: break

            if (curNode is DeletedNode && prevNode.next[level].compareAndSet(curNode, nextNode)) {
                curNode = nextNode
            } else {
                prevNode = curNode
                curNode = nextNode
            }
        }

        val nextNodeAtomic = curNode.next[level]
        val nextNode = nextNodeAtomic.value

        return NodeSubset(prevNode, curNode, nextNode)
    }

    private fun isElementPresentedInSubsets(element: E, subsets: Array<NodeSubset<E>?>): Boolean {
        // If the lowest-level subset does not contain an element, so the whole Skip-List does not contain such element.
        return subsets[bottomLevel]!!.containsElement(element)
    }

    inner class NodeSubset<E : Comparable<E>>(val prev: Node<E>?, val curr: Node<E>?, val next: Node<E>?) {
        fun containsElement(element: E): Boolean {
            return curr?.element == element || next?.element == element
        }
    }
}

open class Node<E : Comparable<E>>(element: E?, levelsCount: Int) {
    private val _element: E? = element
    val element get() = _element

    private val _next = atomicArrayOfNulls<Node<E>?>(levelsCount)
    val next get() = _next

    private val _levelsCount: Int = levelsCount
    val levelsCount get() = _levelsCount
}

private class DeletedNode<E : Comparable<E>>(element: E?, levelsCount: Int) : Node<E>(element, levelsCount)