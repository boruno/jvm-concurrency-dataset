@file:Suppress("DuplicatedCode")

//package day1

import Bank.Companion.MAX_AMOUNT
import kotlinx.atomicfu.locks.withLock
import java.util.concurrent.locks.*

class CoarseGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    // TODO: use this mutex to protect all bank operations.
    private val globalLock = ReentrantLock()

    override fun getAmount(id: Int): Long {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        globalLock.withLock {
            return accounts[id].amount
        }
    }

    override fun deposit(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        var accAmount = account.amount
        globalLock.withLock {
            check(!(amount > MAX_AMOUNT || accAmount + amount > MAX_AMOUNT)) { "Overflow" }
            accAmount += amount
        }
        return accAmount
    }

    override fun withdraw(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        var accAmount = account.amount
        globalLock.withLock {
            check(accAmount - amount >= 0) { "Underflow" }
            accAmount -= amount
        }
        return accAmount
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromIndex == toIndex" }
        val from = accounts[fromId]
        val to = accounts[toId]
        var from_amount = from.amount
        var to_amount = to.amount
        globalLock.withLock {
            check(amount <= from_amount) { "Underflow" }
            check(!(amount > MAX_AMOUNT || to_amount + amount > MAX_AMOUNT)) { "Overflow" }
            from_amount -= amount
            to_amount += amount

            from.amount = from_amount
            to.amount = to.amount
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