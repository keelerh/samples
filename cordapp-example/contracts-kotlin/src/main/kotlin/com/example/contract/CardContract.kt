package com.example.contract

import com.example.state.CardState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

/**
 * This contract enforces rules regarding the creation of a valid [CardState].
 *
 * For a new [CardState] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [CardState].
 * - A Create() command with the public keys of both the dealer and the player.
 */
class CardContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.example.contract.CardContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for
     * a transaction to be considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands.Create>()
        requireThat {
            // Generic constraints around the Deal transaction.
            "No inputs should be consumed when dealing new cards." using (tx.inputs.isEmpty())
            "Only one output state should be created." using (tx.outputs.size == 1)
            val out = tx.outputsOfType<CardState>().single()
            "The dealer and the player cannot be the same entity." using (out.dealer != out.player)
            "All of the participants must be signers." using (command.signers.containsAll(
                    out.participants.map { it.owningKey }))

            // Card-specific constraints.
            // TODO: Check that the card is in the deck of valid cards.
            // "The card must be a valid." using (out.card in Deck)
        }
    }

    /**
     * This contract only implements one command, Create.
     */
    interface Commands : CommandData {
        class Create : Commands
    }
}
