package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.lang.Exception
import java.util.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    companion object {
        val random = Random()
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val random = random.nextLong()
        while (true) {
            val cur_node = top.value
            val node = Node(x, cur_node)
            if (top.compareAndSet(cur_node, node)) {
                return
            } else {
                val i = 0
                val y = KeyPair("push", x)
                if (eliminationArray[i].compareAndSet(null, y)) {
                    val e = eliminationArray[i].value
                    System.out.println("$random $e $y")
                    if (eliminationArray[i].compareAndSet(y, null)) {
                        continue
                    } else {
                        val z = eliminationArray[i].value
                        System.out.println("r $random $z $y")
                        return
                    }
                }
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val random = Random()
        while (true) {
            val cur_node = top.value ?: return null
            val node = cur_node.next
            if (top.compareAndSet(cur_node, node)) {
                return cur_node.x
            } else {
                val i = 0
                var x = eliminationArray[i].value ?: continue
                x = (x as Pair<*, *>).second ?: continue
                @Suppress("UNCHECKED_CAST")
                if (eliminationArray[i].compareAndSet(KeyPair("push", x), null)) {
                    System.err.println("pop $x")
                    return x as E
                }
            }
        }
    }
}

class KeyPair<K, V>(val key: K, val value: V) {
    override fun equals(other: Any?): Boolean {
        return try {
            val otherPair = other as KeyPair<*, *>
            otherPair.key?.equals(key) ?: false
        } catch (e: Exception) {
            false
        }
    }

    override fun toString(): String {
        return "KeyPair($key, $value)"
    }

    override fun hashCode(): Int {
        var result = key?.hashCode() ?: 0
        result = 31 * result + (value?.hashCode() ?: 0)
        return result
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT