package com.example.state

import net.corda.core.identity.Party

data class DeckState (val cards: List<Card>, val dealer: Party)