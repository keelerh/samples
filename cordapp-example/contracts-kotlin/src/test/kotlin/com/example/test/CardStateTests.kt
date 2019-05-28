package com.example.state

import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class CardStateTests {
    private val dealer = TestIdentity(CordaX500Name("dealer", "New York", "US"))
    private val alice = TestIdentity(CordaX500Name("alice", "Tokyo", "JP"))

    @Before
    fun setup() {
    }

    @After
    fun tearDown() {
    }

    @Test
    fun `create CardState`() {
        var card = Card("spike", "1")
        var state = CardState(card, dealer.party, alice.party, 0)
        assertEquals(state.dealer.name.organisation, "dealer")
        assertEquals(state.participants.size, 2)


    }


}