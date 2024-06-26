package mpp.skiplist

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class SkipList<E : Comparable<E>> {
    private val levelsCount: Int = 10
    private val head = atomic(Node<E>(element = null, levelsCount = levelsCount))

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        val bottomLevel: Int = 0
        val maxNodeLevel: Int = ThreadLocalRandom.current().nextInt(0, levelsCount)

        while (true) {
            var nodeSubsets = find(element)

            if (isElementPresentedInSubsets(element, nodeSubsets))
                return false

            val newNode = Node(element, maxNodeLevel)

            // Setting the 'next' node for new node on all levels.
            // Visible for one thread only.
            for (level in bottomLevel until maxNodeLevel) {
                val levelSubset = nodeSubsets[level]
                val newNodeNext = levelSubset?.next

                newNode.next[level].getAndSet(newNodeNext)
            }

            val bottomSubset = nodeSubsets[bottomLevel]

            // If unsuccessful - something has changed in Skip-List structure, so try again from the start.
            val bottomPreviousNode = bottomSubset?.prev!!
            if (!bottomPreviousNode.next[bottomLevel].compareAndSet(bottomSubset.next, newNode))
                continue

            // Reconnecting higher-level nodes.
            for (level in bottomLevel + 1 until maxNodeLevel) {
                while (true) {
                    val levelSubset = nodeSubsets[level]

                    // Finish iteration for current level if successful CAS.
                    val levelPreviousNode = levelSubset?.prev!!
                    if (levelPreviousNode.next[level].compareAndSet(levelSubset.next, newNode))
                        break

                    nodeSubsets = find(element)
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
        val bottomLevel: Int = 0

        while (true) {
            var nodeSubsets = find(element)

            if (!isElementPresentedInSubsets(element, nodeSubsets))
                return false

            val removingNode = nodeSubsets[bottomLevel]?.curr!!

            TODO("Not implemented")
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val nodeSubsets = find(element)
        return isElementPresentedInSubsets(element, nodeSubsets)
    }

    private fun find(targetElement: E): Array<NodeSubset<E>?> {
        val bottomLevel: Int = 0
        val nodeSubsets: Array<NodeSubset<E>?> = arrayOfNulls(levelsCount)

        while (true) {
            val prevNode = head.value

            for (level in levelsCount downTo bottomLevel) {
                // Retry the whole cycle on the updated structure.
                val nodeOnCurrentLevel = findNodeOnLevel(prevNode, targetElement, level) ?: break
                nodeSubsets[level] = nodeOnCurrentLevel
            }

            return nodeSubsets
        }
    }

    private fun findNodeOnLevel(prevNode: Node<E>, targetElement: E, level: Int): NodeSubset<E>? {
        var previousNode = prevNode
        var currentNode = previousNode.next[level].value

        while (true) {
            val nextExistingNode = findNextExistingNode(currentNode!!, previousNode, level) ?: return null

            if (nextExistingNode.curr?.element!! < targetElement) {
                previousNode = nextExistingNode.curr
                currentNode = nextExistingNode.next
            } else {
                return nextExistingNode
            }
        }
    }


    private fun findNextExistingNode(currNode: Node<E>, prevNode: Node<E>, level: Int): NodeSubset<E>? {
        var currentNode = currNode
        var previousNode = prevNode

        var nextNode = currentNode.next[level].value

        while (nextNode is DeletedNode) {
            if (!previousNode.next[level].compareAndSet(currentNode, nextNode))
                return null

            currentNode = previousNode.next[level].value!!
            nextNode = currentNode.next[level].value
        }

        return NodeSubset(previousNode, currentNode, nextNode)
    }

    private fun isElementPresentedInSubsets(element: E, subsets: Array<NodeSubset<E>?>): Boolean {
        val bottomLevel: Int = 0

        // If the lowest-level subset does not contain an element, so the whole Skip-List does not contain such element.
        return subsets[bottomLevel]?.containsElement(element) ?: false
    }

    inner class NodeSubset<E : Comparable<E>>(val prev: Node<E>?, val curr: Node<E>?, val next: Node<E>?) {
        fun containsElement(element: E): Boolean {
            return curr?.element == element
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

private class DeletedNode<E : Comparable<E>>(element: E?, topLevel: Int) : Node<E>(element, topLevel)