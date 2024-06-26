package mpp.fgbank

import kotlinx.atomicfu.locks.*
import java.util.concurrent.locks.ReentrantLock

class FineGrainedBank(n: Int) {
    private val accounts: Array<Account> = Array(n) { Account() }

    private val lock = ReentrantLock()

    private fun lock() = lock.lock()
    private fun unlock() = lock.unlock()

    /**
     * Returns current amount in the specified account.
     *
     * @param index account index from 0 to [n][numberOfAccounts]-1.
     * @return amount in account.
     * @throws IndexOutOfBoundsException when index is invalid account index.
     */
    fun getAmount(index: Int): Long {
        accounts[index].lock()
        try {
            return accounts[index].amount
        } finally {
            accounts[index].unlock()
        }
    }

    /**
     * Returns total amount deposited in this bank.
     */
    fun totalAmount(): Long {
        lock()
        try {
            return accounts.sumOf { account ->
                account.amount
            }
        } finally {
            unlock()
        }
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
        accounts[index].lock()
        try {
            require(amount > 0) { "Invalid amount: $amount" }
            val account = accounts[index]
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            return account.amount
        } finally {
            accounts[index].unlock()
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
        accounts[index].lock()
        try {
            require(amount > 0) { "Invalid amount: $amount" }
            val account = accounts[index]
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            return account.amount
        } finally {
            accounts[index].unlock()
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
        lock()
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromIndex != toIndex) { "fromIndex == toIndex" }
        try {
            accounts[fromIndex].lock()
            try {
                val from = accounts[fromIndex]
                accounts[toIndex].lock()
                try {
                    val to = accounts[toIndex]
                    check(amount <= from.amount) { "Underflow" }
                    check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
                    from.amount -= amount
                    to.amount += amount
                } finally {
                    accounts[toIndex].unlock()
                }
            } finally {accounts[fromIndex].unlock()}
        } finally {unlock()}
    }

    /**
     * Private account data structure.
     */
    class Account {
        private val lock = ReentrantLock()
        fun lock() = lock.lock()
        fun unlock() = lock.unlock()
        /**
         * Amount of funds in this account.
         */
        var amount: Long = 0
    }
}

/**
 * The maximal amount that can be kept in a bank account.
 */
private const val MAX_AMOUNT = 1000000000000000L
