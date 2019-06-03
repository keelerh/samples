package com.example.state

import com.example.contract.GameContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

/**
 */
@BelongsToContract(GameContract::class)
data class GameState(val communityCards: List<Card>,
                     val dealer: Party,
                     val players: List<Party>,
                     val gameId: Int,
                     val cardsRevealedByPlayer: Map<Party, List<Card>>): ContractState {
    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = mutableListOf<AbstractParty>(dealer).plus(players)
}

