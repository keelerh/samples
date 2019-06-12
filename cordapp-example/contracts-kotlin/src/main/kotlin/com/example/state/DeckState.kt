package com.example.state

import com.google.common.collect.ImmutableList
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

    fun dealOneCard(): Card {
        return cards.removeAt(0)
    }

    fun dealXCards(x: Int): List<Card> {
//        return listOf(Card(Card.Suit.DIAMONDS, Card.Rank.FIVE),
//                Card(Card.Suit.DIAMONDS, Card.Rank.SIX),
//                Card(Card.Suit.DIAMONDS, Card.Rank.SEVEN),
//                Card(Card.Suit.DIAMONDS, Card.Rank.EIGHT))
        return cards.subList(0, x)
    }
}
