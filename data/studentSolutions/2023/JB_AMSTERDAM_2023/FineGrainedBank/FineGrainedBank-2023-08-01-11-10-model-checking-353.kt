@file:Suppress("DuplicatedCode")

package day1

import day1.Bank.Companion.MAX_AMOUNT
import kotlinx.atomicfu.locks.withLock
import java.util.concurrent.locks.*

class FineGrainedBank(accountsNumber: Int) : Bank {

    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    override fun getAmount(id: Int): Long =
        accounts[id].withLock { amount }

    override fun deposit(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        return accounts[id].withLock {
            check(!(amount > MAX_AMOUNT || this.amount + amount > MAX_AMOUNT)) { "Overflow" }
            this.amount += amount
            this.amount
        }
    }

    override fun withdraw(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        return accounts[id].withLock {
            check(this.amount - amount >= 0) { "Underflow" }
            this.amount -= amount
            this.amount
        }
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromId == toId" }
        (accounts[fromId] to accounts[toId]).withLocks { (from, to) ->
            check(amount <= from.amount) { "Underflow" }
            check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
            from.amount -= amount
            to.amount += amount
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

        private val lock = ReentrantLock()

        fun <T> withLock(op: Account.() -> T) = lock.withLock { this.op() }
    }

    fun <T> Pair<Account, Account>.withLocks(op: (Pair<Account, Account>) -> T) {
        first.withLock {
            second.withLock {
                op(this@withLocks)
            }
        }
    }
}