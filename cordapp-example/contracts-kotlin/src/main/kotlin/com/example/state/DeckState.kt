package com.example.state

class Deck {
    var cards: MutableList<Card> = mutableListOf()

    init {
        for (suit in Card.Suit.values())
            for (rank in Card.Rank.values())
                cards.add(Card(suit, rank))
    }

    fun shuffle() = cards.shuffle()

    fun dealOneCard(): Card? {
        return if (cards.isNotEmpty())
            cards.removeAt(0)
        else
            null
    }
}
