package com.example.state

import com.example.contract.CardContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

/**
 */
@BelongsToContract(CardContract::class)
data class CardState(val card: Card,
                     val dealer: Party,
                     val player: Party,
                     val gameId: Int): ContractState {
    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(dealer, player)
}

@CordaSerializable
data class Card(val suit: String, val value: String) {
    fun isValid(): Boolean {
        return true
    }
}
