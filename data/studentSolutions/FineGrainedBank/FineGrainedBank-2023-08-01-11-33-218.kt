@file:Suppress("DuplicatedCode")

//package day1

import Bank.Companion.MAX_AMOUNT
import java.util.concurrent.locks.*
import kotlin.concurrent.withLock

class FineGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    override fun getAmount(id: Int): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        val account = accounts[id]
        return account.lock.withLock {
            account.amount
        }
    }

    override fun deposit(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        return account.lock.withLock {
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            account.amount
        }
    }

    override fun withdraw(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        return account.lock.withLock {
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            account.amount
        }
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromId == toId" }
        val from = accounts[fromId]
        val to = accounts[toId]
        synchronized(this) {
            from.lock.lock()
            to.lock.lock()
        }
        try {
            check(amount <= from.amount) { "Underflow" }
            check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
            from.amount -= amount
            to.amount += amount
        } finally {
            synchronized(this) {
                from.lock.unlock()
                to.lock.unlock()
            }
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