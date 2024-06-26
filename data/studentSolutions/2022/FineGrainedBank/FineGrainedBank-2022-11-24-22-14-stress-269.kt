package mpp.fgbank

import kotlinx.atomicfu.locks.*
import java.util.concurrent.locks.ReentrantLock

class FineGrainedBank(n: Int) {
    private val accounts: Array<Account> = Array(n) { Account() }

    /**
     * Returns current amount in the specified account.
     *
     * @param index account index from 0 to [n][numberOfAccounts]-1.
     * @return amount in account.
     * @throws IndexOutOfBoundsException when index is invalid account index.
     */
    fun getAmount(index: Int): Long {
        // TODO: this method has to be made thread-safe.
        return accounts[index].getValue()
    }

    /**
     * Returns total amount deposited in this bank.
     */
    fun totalAmount(): Long {
        // TODO: this method has to be made thread-safe.
        var ans = 0L
        accounts.forEach {
            it.lock
        }
        ans = accounts.sumOf { account ->
            account.amount
        }
        accounts.forEach {
            it.unlock()
        }
        return ans
    }

    /**
     * Deposits specified amount to account.
     *
     * @param index account index from 0 to [n][numberOfAccounts]-1.
     * @param amount positive amount to deposit.
     * @return resulting amount in account.
     * @throws IllegalArgumentException when amount <= 0.
     * @throws IndexOutOfBoundsException when index is invalid account index.
     * @throws IllegalStateException when deposit will overflow account above [MAX_AMOUNT].
     */
    fun deposit(index: Int, amount: Long): Long {
        // TODO: this method has to be made thread-safe.
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[index]
        account.lock.withLock {
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            return account.amount
        }
    }

    /**
     * Withdraws specified amount from account.
     *
     * @param index account index from 0 to [n][numberOfAccounts]-1.
     * @param amount positive amount to withdraw.
     * @return resulting amount in account.
     * @throws IllegalArgumentException when amount <= 0.
     * @throws IndexOutOfBoundsException when index is invalid account index.
     * @throws IllegalStateException when account does not enough to withdraw.
     */
    fun withdraw(index: Int, amount: Long): Long {
        // TODO: this method has to be made thread-safe.
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[index]
        account.lock.withLock {
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            return account.amount
        }
    }

    /**
     * Transfers specified amount from one account to another account.
     *
     * @param fromIndex account index to withdraw from.
     * @param toIndex account index to deposit to.
     * @param amount positive amount to transfer.
     * @throws IllegalArgumentException when amount <= 0 or fromIndex == toIndex.
     * @throws IndexOutOfBoundsException when account indices are invalid.
     * @throws IllegalStateException when there is not enough funds in source account or too much in target one.
     */
    fun transfer(fromIndex: Int, toIndex: Int, amount: Long) {
        // TODO: this method has to be made thread-safe.
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromIndex != toIndex) { "fromIndex == toIndex" }
        val from = accounts[fromIndex]
        from.lock()
        val to = accounts[toIndex]
        to.unlock()
        check(amount <= from.amount) {
            from.unlock()
            to.unlock()
            "Underflow"
        }
        check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) {
            from.unlock()
            to.unlock()
            "Overflow"
        }
        from.amount -= amount
        from.unlock()
        to.amount += amount
        to.unlock()

    }

    /**
     * Private account data structure.
     */
    class Account {
        /**
         * Amount of funds in this account.
         */
        var amount: Long = 0

        var lock = ReentrantLock()

        fun getValue() = lock.withLock {
            amount
        }

        fun lock() = lock.lock()

        fun unlock() = lock.unlock()
    }
}

/**
 * The maximal amount that can be kept in a bank account.
 */
private const val MAX_AMOUNT = 1000000000000000L
