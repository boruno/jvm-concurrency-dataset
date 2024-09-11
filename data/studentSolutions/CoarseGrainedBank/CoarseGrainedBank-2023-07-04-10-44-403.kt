@file:Suppress("DuplicatedCode")

package day1

import day1.Bank.Companion.MAX_AMOUNT
import java.util.concurrent.locks.*

class CoarseGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    // TODO: use this mutex to protect all bank operations.
    private val globalLock = ReentrantLock()

    override fun getAmount(id: Int): Long {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        globalLock.lock()
        val amount: Long
        try {
            amount = accounts[id].amount
        } finally {
            globalLock.unlock()
        }
        return amount
    }

    override fun deposit(id: Int, amount: Long): Long {
        // TOval amount1DO: Make this operation thread-safe via coarse-grained locking.
        globalLock.lock()
        val amount1: Long
        try {
            require(amount > 0) { "Invalid amount: $amount" }
            val account = accounts[id]
            amount1 = account.amount
            check(!(amount > MAX_AMOUNT || amount1 + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
        } finally {
            globalLock.unlock()
        }
        return amount1
    }

    override fun withdraw(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        globalLock.lock()
        val amount1: Long
        try {
            require(amount > 0) { "Invalid amount: $amount" }
            val account = accounts[id]
            amount1 = account.amount
            check(amount1 - amount >= 0) { "Underflow" }
            account.amount -= amount
        } finally {
            globalLock.unlock()
        }
        return amount1;
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        globalLock.lock()
        try {
            require(amount > 0) { "Invalid amount: $amount" }
            require(fromId != toId) { "fromIndex == toIndex" }
            val from = accounts[fromId]
            val to = accounts[toId]
            check(amount <= from.amount) { "Underflow" }
            check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
            from.amount -= amount
            to.amount += amount
        } finally {
            globalLock.unlock()
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
    }
}