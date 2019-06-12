package com.example.state

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
class Deck {
    var cards: MutableList<Card> = mutableListOf()

    init {
        for (suit in Card.Suit.values())
            for (rank in Card.Rank.values())
                cards.add(Card(suit, rank))
    }

    fun shuffle() = cards.shuffle()

    fun dealXCards(x: Int): List<Card> {
        return cards.subList(0, x)
    }
}
