package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)


    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {

        if ( eliminationArray[ 0 ].equals( null ) ) {
            var i : Int
            i = 0
            eliminationArray[ 0 ].getAndSet( x )
            while ( i < 100 ) {
                i++
                if ( eliminationArray[ 0 ].equals( null ) )
                    return;
            }
            eliminationArray[ 0 ].getAndSet( null )
        } else {
            var i : Int
            i = 0
            eliminationArray[ 1 ].getAndSet( x )
            while ( i < 100 ) {
                i++
                if ( eliminationArray[ 1 ].equals( null ) )
                    return;
            }
            eliminationArray[ 1 ].getAndSet( null )
        }
        while ( true ) {
            var cur_top = top.value
            var new_top = Node<E>( x, cur_top )
            if (top.compareAndSet(cur_top, new_top)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        if ( !eliminationArray[ 0 ].equals( null ) ) {
//            var ans : E?
//            ans = eliminationArray[ 0 ].getAndSet( null ) as E?
//            return ans
            return eliminationArray[ 1 ].getAndSet( null ) as E?
        }
        if ( !eliminationArray[ 1 ].equals( null ) ) {
            return eliminationArray[ 1 ].getAndSet( null ) as E?
        }
        while ( true ) {
            var cur_top = top.value
            if ( cur_top != null ) {
                var new_top = cur_top.next
                if ( top.compareAndSet( cur_top, new_top ) ) {
                    return cur_top.x
                }
            } else {
                return null
            }
        }
    }

}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT