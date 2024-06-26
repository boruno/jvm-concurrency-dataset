@file:Suppress("DuplicatedCode")

package day1

import day1.Bank.Companion.MAX_AMOUNT
import java.util.concurrent.locks.ReentrantLock

class FineGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    private val globalLock = ReentrantLock()

    private inline fun <T> withLock(block: () -> T): T {
        globalLock.lock()
        val res: T
        try {
            res = block()
        } finally {
            globalLock.unlock()
        }
        return res
    }

    private inline fun <T> withLock(account: Account, block: () -> T): T {
        account.lock.lock()
        val res: T
        try {
            res = block()
        } finally {
            account.lock.unlock()
        }
        return res
    }

    override fun getAmount(id: Int): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        val account = withLock { accounts[id] }
        return withLock(account) { account.amount }
    }

    override fun deposit(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        val account = withLock {
            require(amount > 0) { "Invalid amount: $amount" }
            accounts[id]
        }
        return withLock(account) {
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            account.amount
        }
    }

    override fun withdraw(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        val account = withLock {
            require(amount > 0) { "Invalid amount: $amount" }
            accounts[id]
        }
        return withLock(account) {
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            account.amount
        }
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromId == toId" }
        val from = withLock { accounts[fromId] }
        return withLock(from) {
            val to = withLock { accounts[toId] }
            check(amount <= from.amount) { "Underflow" }
            check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
            from.amount -= amount
            to.amount += amount
//            withLock(to) {
//            }
        }
    }

    /**
     * Private account data structure.
     */
    class Account {
        /**
         * Amount of funds in this account.
         */
        var amount: Long = 0

        /**
         * TODO: use this mutex to protect the account data.
         */
        val lock = ReentrantLock()
    }
}
