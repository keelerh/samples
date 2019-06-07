package com.example.test.contract

import com.example.contract.CardContract
import com.example.state.Card
import com.example.state.CardState
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test


class CardContractTests {
    companion object {
        @JvmStatic val TEST_CARD = Card(Card.Suit.SPADES, Card.Rank.QUEEN)
        @JvmStatic val TEST_GAME_ID = 1
    }

    private val ledgerServices = MockServices(listOf("com.example.contract", "com.example.flow"))
    private val dealer = TestIdentity(CordaX500Name("Dealer", "London", "GB"))
    private val player = TestIdentity(CordaX500Name("Player", "New York", "US"))

    @Test
    fun `transaction must include Create command`() {
        ledgerServices.ledger {
            transaction {
                output(CardContract.ID, CardState(
                        TEST_CARD, dealer.party, player.party, TEST_GAME_ID))
                fails()
                command(listOf(dealer.publicKey, player.publicKey), CardContract.Commands.Create())
                verifies()
            }
        }
    }

    //@Test
    fun `transaction must have no inputs`() {
        ledgerServices.ledger {
            transaction {
                input(CardContract.ID, CardState(TEST_CARD, dealer.party, player.party, TEST_GAME_ID))
                output(CardContract.ID, CardState(TEST_CARD, dealer.party, player.party, TEST_GAME_ID))
                command(listOf(dealer.publicKey, player.publicKey), CardContract.Commands.Create())
                `fails with`("No inputs should be consumed when dealing VALID_CARDs.")
            }
        }
    }
    //@Test
    fun `transaction must have one output`() {
        ledgerServices.ledger {
            transaction {
                input(CardContract.ID, CardState(TEST_CARD, dealer.party, player.party, TEST_GAME_ID))
                output(CardContract.ID, CardState(TEST_CARD, dealer.party, player.party, TEST_GAME_ID))
                command(listOf(dealer.publicKey, player.publicKey), CardContract.Commands.Create())
                `fails with`("Only one output state should be created.")
            }
        }
    }

    //@Test
    fun `dealer must sign transaction`() {
        ledgerServices.ledger {
            transaction {
                output(CardContract.ID, CardState(TEST_CARD, dealer.party, player.party, TEST_GAME_ID))
                command(dealer.publicKey, CardContract.Commands.Create())
                `fails with`("All of the participants must be signers.")
            }
        }
    }

    @Test
    fun `player must sign transaction`() {
        ledgerServices.ledger {
            transaction {
                output(CardContract.ID, CardState(TEST_CARD, dealer.party, player.party, TEST_GAME_ID))
                command(player.publicKey, CardContract.Commands.Create())
                `fails with`("All of the participants must be signers.")
            }
        }
    }

    @Test
    fun `dealer is not player`() {
        ledgerServices.ledger {
            transaction {
                output(CardContract.ID, CardState(TEST_CARD, dealer.party, dealer.party, TEST_GAME_ID))
                command(listOf(dealer.publicKey, player.publicKey), CardContract.Commands.Create())
                `fails with`("The dealer and the player cannot be the same entity.")
            }
        }
    }

    //@Test
    fun `VALID_CARD must be valid`() {
        ledgerServices.ledger {
            transaction {
                output(CardContract.ID, CardState(TEST_CARD, dealer.party, dealer.party, TEST_GAME_ID))
                command(listOf(dealer.publicKey, player.publicKey), CardContract.Commands.Create())
                `fails with`("The TEST_CARD must be valid.")
            }
        }
    }
}