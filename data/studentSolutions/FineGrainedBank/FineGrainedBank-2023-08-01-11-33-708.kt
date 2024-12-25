@file:Suppress("DuplicatedCode")

//package day1

import day1.Bank.Companion.MAX_AMOUNT
import kotlinx.atomicfu.locks.withLock
import java.util.concurrent.locks.*

class FineGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }


    //private val locks = mutableMapOf<Int, ReentrantLock>()

    override fun getAmount(id: Int): Long = accounts[id].lock.withLock {
        val account = accounts[id]
        return account.amount
    }

    override fun deposit(id: Int, amount: Long): Long = accounts[id].lock.withLock {
        //println("locks =" + locks.size)
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
        account.amount += amount
        return account.amount
    }

    override fun withdraw(id: Int, amount: Long): Long = accounts[id].lock.withLock {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        check(account.amount - amount >= 0) { "Underflow" }
        account.amount -= amount
        return account.amount

    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {

        accounts[fromId].lock.withLock {
            accounts[toId].lock.withLock {

                // TODO: Make this operation thread-safe via fine-grained locking.
                require(amount > 0) { "Invalid amount: $amount" }
                require(fromId != toId) { "fromId == toId" }
                val from = accounts[fromId]
                val to = accounts[toId]
                check(amount <= from.amount) { "Underflow" }
                check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
                from.amount -= amount
                to.amount += amount
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