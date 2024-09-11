import kotlinx.atomicfu.*
import TxStatus.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    class Ref<T>(initial: T) {
        private val v = atomic(initial)

        var value: T
            get() = v.value
            set(value) {
                v.value = value
            }

        fun cas(expected: T, update: T) = v.compareAndSet(expected, update)
        fun gas(update: T) = v.getAndSet(update)

    }

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.value

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.cas(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean = atomic {
        if (a[index1].value != expected1 || a[index2].value != expected2) return@atomic false
        a[index1].value?.value = update1
        a[index2].value?.value = update2
        return@atomic true
    }
}

data class CasData<E>(
    val index: Int,
    val expected: E,
    val update: E
)

/**
 * Atomic block.
 */
fun <T> atomic(block: TxScope.() -> T): T {
    while (true) {
        val transaction = Transaction()
        try {
            val result = block(transaction)
            if (transaction.commit()) return result
            transaction.abort()
        } catch (e: AbortException) {
            transaction.abort()
        }
    }
}

/**
 * Transactional operations are performed in this scope.
 */
abstract class TxScope {
    abstract fun <T> TxVar<T>.read(): T
    abstract fun <T> TxVar<T>.write(x: T): T
}

/**
 * Transactional variable.
 */
class TxVar<T>(initial: T)  {
    private val loc = atomic(Loc(initial, initial, rootTx))

    /**
     * Opens this transactional variable in the specified transaction [tx] and applies
     * updating function [update] to it. Returns the updated value.
     */
    @Suppress("UNCHECKED_CAST")
    fun openIn(tx: Transaction, update: (T) -> T): T {
        while (true) {
            val curLoc = loc.value
            val curValue = curLoc.valueIn(tx) { it.abort() }
            if (curValue == ACTIVE) continue
            val updValue = update(curValue as T)
            val updLoc = Loc(curValue, updValue, tx)
            if (loc.compareAndSet(curLoc, updLoc)) {
                if (tx.status == ABORTED)
                    throw AbortException
                return updValue
            }
        }
    }
}

/**
 * State of transactional value
 */
private class Loc<T>(
    val oldValue: T,
    val newValue: T,
    val owner: Transaction
) {
    fun valueIn(tx: Transaction, onActive: (Transaction) -> Unit): Any? {
        return if (tx == owner) newValue else {
            when (owner.status) {
                COMMITTED -> newValue
                ABORTED -> oldValue
                ACTIVE -> {
                    onActive(owner)
                    ACTIVE
                }
            }
        }
    }
}

private val rootTx = Transaction().apply { commit() }

/**
 * Transaction status.
 */
enum class TxStatus { ACTIVE, COMMITTED, ABORTED }

/**
 * Transaction implementation.
 */
class Transaction : TxScope() {
    private val _status = atomic(ACTIVE)
    val status: TxStatus get() = _status.value

    fun commit(): Boolean =
        _status.compareAndSet(ACTIVE, COMMITTED)

    fun abort() {
        _status.compareAndSet(ACTIVE, ABORTED)
    }

    override fun <T> TxVar<T>.read(): T = openIn(this@Transaction) { it }
    override fun <T> TxVar<T>.write(x: T) = openIn(this@Transaction) { x }
}

/**
 * This exception is thrown when transaction is aborted.
 */
private object AbortException : Exception() {
    override fun fillInStackTrace(): Throwable = this
}