package mpp.linkedlistset
// 1 prev 1st 4 1st 5 6 2nd
import kotlinx.atomicfu.*

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
        val new_node = Node(null, element, null)

        while (true) {
            var cur = first
            var cur_nextAndRemoved = cur.nextAndRemoved.value
            var next = cur_nextAndRemoved.first!!
            var next_nextAndRemoved = next.nextAndRemoved.value

            if (!assertCorrectness(cur, cur_nextAndRemoved) || !assertCorrectness(next, next_nextAndRemoved)) { // if something already got removed
                helpRemove(cur, next)
                continue
            }

            new_node.setNext(next)

            while (true) {
                if (cur == first || next == last) {
                    if (cur == first && next == last) {
                        if (cur.nextAndRemoved.compareAndSet(cur_nextAndRemoved, Pair(new_node, false))) {
                            return true// element does not exist
                        }
                        else {
                            break // doesn't make sense to remove, since next is the last node
                        }
                    }

                    if (cur == first && element < next.element) {
                        if (cur.nextAndRemoved.compareAndSet(cur_nextAndRemoved, Pair(new_node, false))) {
                            return true // element does not exist
                        }
                        else {
                            helpRemove(cur, next)
                            break
                        }
                    }

                    if (cur.element < element && next == last) {
                        if (cur.nextAndRemoved.compareAndSet(cur_nextAndRemoved, Pair(new_node, false))) {
                            return true// element does not exist
                        }
                        else {
                            break // doesn't make sense to remove, since next is the last node
                        }
                    }
                }

                if (checkEquals(cur, element)) {
                    return false // element exists
                }

                if (cur.element < element && element < next.element) {
                    if (cur.nextAndRemoved.compareAndSet(cur_nextAndRemoved, Pair(new_node, false))) {
                        return true // element does not exist
                    }
                    else {
                        helpRemove(cur, next)
                        break
                    }
                }

                cur = next
                cur_nextAndRemoved = next_nextAndRemoved
                next = cur_nextAndRemoved.first!!
                next_nextAndRemoved = next.nextAndRemoved.value

                if (!assertCorrectness(cur, cur_nextAndRemoved) || !assertCorrectness(next, next_nextAndRemoved)) { // go further if nothing changed and everything is correct
                    helpRemove(cur, next)
                    break
                }

                new_node.setNext(next)
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
            var cur = first
            var cur_nextAndRemoved = cur.nextAndRemoved.value
            var next = cur_nextAndRemoved.first!!
            var next_nextAndRemoved = next.nextAndRemoved.value

            if (!assertCorrectness(cur, cur_nextAndRemoved) || !assertCorrectness(next, next_nextAndRemoved)) { // if something already got removed
                helpRemove(cur, next)
                continue
            }

            while (true) {
                if (cur == first || next == last) {
                    if (cur == first && next == last) {
                        if (checkCorrectState(cur, next)) {
                            return false // element does not exist
                        }
                        else {
                            break // doesn't make sense to remove, since next is the last node
                        }
                    }

                    if (cur == first && element < next.element) {
                        if (checkCorrectState(cur, next)) {
                            return false // element does not exist
                        }
                        else {
                            helpRemove(cur, next)
                            break
                        }
                    }

                    if (cur.element < element && next == last) {
                        if (checkCorrectState(cur, next)) {
                            return false // element does not exist
                        }
                        else {
                            break // doesn't make sense to remove, since next is the last node
                        }
                    }
                }

                if (checkEquals(next, element)) {
                    if (next.nextAndRemoved.compareAndSet(next_nextAndRemoved, Pair(next_nextAndRemoved.first, true))) {
                        helpRemove(cur, next)
                        return true
                    }
                    else {
                        break
                    }
                }

                if (cur.element < element && element < next.element) {
                    if (checkCorrectState(cur, next)) {
                        return false // element does not exist
                    }
                    else {
                        helpRemove(cur, next)
                        break
                    }
                }

                cur = next
                cur_nextAndRemoved = next_nextAndRemoved
                next = cur_nextAndRemoved.first!!
                next_nextAndRemoved = next.nextAndRemoved.value

                if (!assertCorrectness(cur, cur_nextAndRemoved) || !assertCorrectness(next, next_nextAndRemoved)) { // go further if nothing changed and everything is correct
                    helpRemove(cur, next)
                    break
                }
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        while (true) {
            var cur = first
            var cur_nextAndRemoved = cur.nextAndRemoved.value
            var next = cur_nextAndRemoved.first!!
            var next_nextAndRemoved = next.nextAndRemoved.value

            if (!assertCorrectness(cur, cur_nextAndRemoved) || !assertCorrectness(next, next_nextAndRemoved)) { // if something already got removed
                helpRemove(cur, next)
                continue
            }

            while (true) {
                if (cur == first || next == last) {
                    if (cur == first && next == last) {
                        if (checkCorrectState(cur, next)) {
                            return false // element does not exist
                        }
                        else {
                            break // doesn't make sense to remove, since next is the last node
                        }
                    }

                    if (cur == first && element < next.element) {
                        if (checkCorrectState(cur, next)) {
                            return false // element does not exist
                        }
                        else {
                            helpRemove(cur, next)
                            break
                        }
                    }

                    if (cur.element < element && next == last) {
                        if (checkCorrectState(cur, next)) {
                            return false // element does not exist
                        }
                        else {
                            break // doesn't make sense to remove, since next is the last node
                        }
                    }
                }

                if (checkEquals(cur, element)) {
                    return true // element exists
                }

                if (cur.element < element && element < next.element) {
                    if (checkCorrectState(cur, next)) {
                        return false // element does not exist
                    }
                    else {
                        helpRemove(cur, next)
                        break
                    }
                }

                cur = next
                cur_nextAndRemoved = next_nextAndRemoved
                next = cur_nextAndRemoved.first!!
                next_nextAndRemoved = next.nextAndRemoved.value

                if (!assertCorrectness(cur, cur_nextAndRemoved) || !assertCorrectness(next, next_nextAndRemoved)) { // go further if nothing changed and everything is correct
                    helpRemove(cur, next)
                    break
                }
            }
        }
    }

    private fun helpRemove(node1: Node<E>, node2: Node<E>): Boolean { // atomically help node1 remove node2, return true if it can
        val node1_nextAndRemoved = node1.nextAndRemoved.value
        val node2_nextAndRemoved = node2.nextAndRemoved.value
        if (node2_nextAndRemoved.second == true) { // second value should be true meaning it should be removed
            if (node1_nextAndRemoved.first == node2 && node1_nextAndRemoved.second == false) {
                return node1.nextAndRemoved.compareAndSet(node1_nextAndRemoved, Pair(node2_nextAndRemoved.first, false))
            }
        }
        return false // does nothing if there's nothing to remove
    }

    private fun checkEquals(node: Node<E>, element: E): Boolean { // atomically check if node element equals to element
        if (node.element == element) {
            if (node.nextAndRemoved.value.second == false) {
                return true
            }
        }
        return false
    }

    private fun checkCorrectState(node1: Node<E>, // checks if node1 and node2 are connected and both not removed
                                  node2: Node<E>): Boolean {
        val node1_nextAndRemoved = node1.nextAndRemoved.value

        if (node1_nextAndRemoved.first == node2 && node1_nextAndRemoved.second == false) { // assert that node1's remembered nextAndRemoved is correct
            if (node2.nextAndRemoved.value.second == false) { // check if node2 didn't get removed, linearizibility point
                if (node1.nextAndRemoved.value == node1_nextAndRemoved) { // if node1 current state is still what we remembered
                    return true
                }
            }
        }

        return false
    }

    private fun checkCorrectStateExtended(node1: Node<E>, node1_nextAndRemoved:  Pair<Node<E>?, Boolean>, // checks if node1 and node2 are connected, both are correct and not changed
                                  node2: Node<E>, node2_nextAndRemoved:  Pair<Node<E>?, Boolean>): Boolean {
        val cur_node2_nextAndRemoved = node2.nextAndRemoved.value

        if (node1_nextAndRemoved.first == node2 &&
            node1_nextAndRemoved.second == false) { // assert that node1's remembered nextAndRemoved is correct

            if (cur_node2_nextAndRemoved.first == node2_nextAndRemoved.first &&
                cur_node2_nextAndRemoved.second == node2_nextAndRemoved.second &&
                cur_node2_nextAndRemoved.second == false) { // assert that node2's remembered nextAndRemoved is correct

                if (node2.nextAndRemoved.value == node2_nextAndRemoved) { // check if node2 didn't get removed, linearizibility point
                    if (node1.nextAndRemoved.value == node1_nextAndRemoved) { // if node1 current state is still what we remembered
                        return true
                    }
                }
            }
        }

        return false
    }

    private  fun assertCorrectness(node: Node<E>, nextAndRemoved: Pair<Node<E>?, Boolean>): Boolean { // asserts that current state of node is correct and didn't change
        val cur_nextAndRemoved = node.nextAndRemoved.value
        if (nextAndRemoved.second == false) {
            if (cur_nextAndRemoved.first == nextAndRemoved.first && cur_nextAndRemoved.second == nextAndRemoved.second) {
                if (node.nextAndRemoved.value == cur_nextAndRemoved) {
                    return true
                }
            }
        }
        return false
    }

}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

//    private val _prev = atomic(prev)
//    val prev get() = _prev.value
//    fun setPrev(value: Node<E>?) {
//        _prev.value = value
//    }
//    fun casPrev(expected: Node<E>?, update: Node<E>?) =
//        _prev.compareAndSet(expected, update)
//
//    private val _next = atomic(next)
//    val next get() = _next.value
//    fun setNext(value: Node<E>?) {
//        _next.value = value
//    }
//    fun casNext(expected: Node<E>?, update: Node<E>?) =
//        _next.compareAndSet(expected, update)

    val nextAndRemoved = atomic(Pair<Node<E>?, Boolean>(next, false))

    fun setNext(value: Node<E>?) {
        nextAndRemoved.value = Pair(value, false)
    }

//    val _nextAndRemoved = atomic(AtomicMutablePair<E>(next, false))
//
//    class AtomicMutablePair<E : Comparable<E>>(next: Node<E>?, removed: Boolean) {
//        val first = atomic(next)
//        val second = atomic(removed)
//    }
}