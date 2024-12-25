//package mpp.fgbank
import java.util.concurrent.locks.ReentrantLock

class FineGrainedBank(n: Int) {
    /**
     * An array of accounts by index.
     */
    private val accounts: Array<Account?>

    /**
     * Creates new bank instance.
     * @param n the number of accounts (numbered from 0 to n-1).
     */
    init {
        accounts = arrayOfNulls(n)
        for (i in 0 until n) {
            accounts[i] = Account()
        }
    }


    /**
     *
     * :TODO: This method has to be made thread-safe.
     */
    fun getAmount(index: Int): Long {
        accounts[index]!!.lock()
        val result = accounts[index]!!.amount
        accounts[index]!!.unlock()
        return result
    }

    /**
     *
     * :TODO: This method has to be made thread-safe.
     */
    fun totalAmount(): Long {
        var sum: Long = 0
        for (account in accounts) {
            account!!.lock()
            sum += account.amount
        }
        for (i in accounts.indices.reversed()) {
            accounts[i]!!.unlock()
        }
        return sum
    }

    /**
     *
     * :TODO: This method has to be made thread-safe.
     */
    fun deposit(index: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[index]
        account!!.lock()
        if (amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT) {
            account.unlock()
            throw IllegalStateException("Overflow")
        }
        account.amount += amount
        val result = account.amount
        account.unlock()
        return result
    }

    /**
     *
     * :TODO: This method has to be made thread-safe.
     */
    fun withdraw(index: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[index]
        account!!.lock()
        if (account.amount - amount < 0) {
            account.unlock()
            throw IllegalStateException("Underflow")
        }
        account.amount -= amount
        val result = account.amount
        account.unlock()
        return result
    }

    /**
     *
     * :TODO: This method has to be made thread-safe.
     */
    fun transfer(fromIndex: Int, toIndex: Int, amount: Long) {
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromIndex != toIndex) { "fromIndex == toIndex" }
        val from = accounts[fromIndex]
        val to = accounts[toIndex]
        val firstLock = accounts[Math.min(fromIndex, toIndex)]
        val secondLock = accounts[Math.max(fromIndex, toIndex)]
        firstLock!!.lock()
        secondLock!!.lock()
        try {
            check(amount <= from!!.amount) { "Underflow" }
            check(!(amount > MAX_AMOUNT || to!!.amount + amount > MAX_AMOUNT)) { "Overflow" }
            from.amount -= amount
            to!!.amount += amount
        } finally {
            secondLock.unlock()
            firstLock.unlock()
        }
    }

    /**
     * Private account data structure.
     */
    internal class Account {
        /**
         * Amount of funds in this account.
         */
        var amount: Long = 0
        private val lock = ReentrantLock()
        fun lock() {
            lock.lock()
        }

        fun unlock() {
            lock.unlock()
        }
    }
}

/**
 * The maximal amount that can be kept in a bank account.
 */
private const val MAX_AMOUNT = 1000000000000000L
