import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    enum class Type {
        ADD, PEEK, POLL, LOCKED, REMOVED
    }

    private val q = PriorityQueue<E>()
    private val lock = ReentrantLock()
    private val n = 10
    private val list = atomicArrayOfNulls<Pair<Type, E?>>(n)

    private fun listProcess() {
        for (index in 0 until n) {
            val cur = list[index].value ?: continue
            val lockedValue = Pair(Type.LOCKED, null)
            when (cur.first) {
                Type.ADD -> {
                    if (list[index].compareAndSet(cur, lockedValue)) {
                        q.add(cur.second)
                        list[index].compareAndSet(lockedValue, Pair(Type.REMOVED, null))
                    }
                }

                Type.PEEK -> {
                    if (list[index].compareAndSet(cur, lockedValue)) list[index].compareAndSet(
                        lockedValue,
                        Pair(Type.REMOVED, q.peek())
                    )
                }

                Type.POLL -> {
                    if (list[index].compareAndSet(cur, lockedValue)) list[index].compareAndSet(
                        lockedValue,
                        Pair(Type.REMOVED, q.poll())
                    )
                }

                Type.REMOVED -> break

                else -> {
                    assert(false)
                }
            }
        }
    }

    fun poll(): E? {
        return process(Pair(Type.POLL, null))
    }

    fun peek(): E? {
        return process(Pair(Type.PEEK, null))
    }

    fun add(element: E) {
        process(Pair(Type.ADD, element))
    }

    private fun process(pair: Pair<Type, E?>): E? {
        while (true) {
            if (lock.tryLock()) {
                var value: E? = null
                if (pair.first == Type.POLL) value = q.poll()
                if (pair.first == Type.PEEK) value = q.peek()
                if (pair.first == Type.ADD) q.add(pair.second)
                listProcess()
                lock.unlock()
                return value
            }
            val index = Random().nextInt(n)
            if (list[index].compareAndSet(null, Pair(pair.first, pair.second))) {
                while (true) {
                    val cur = list[index].value!!
                    if (cur.first == pair.first) {
                        if (!lock.isLocked && list[index].compareAndSet(cur, null)) break
                        continue
                    }
                    if (cur.first == Type.REMOVED) {
                        list[index].compareAndSet(cur, null)
                        return cur.second
                    }
                }
            }
        }
    }
}
