package com.example.flow.helpers

import net.corda.core.flows.*
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step

/**
 * The progress tracker checkpoints each stage of the flow and outputs the
 * specified messages when each checkpoint is reached in the code. See the
 * 'progressTracker.currentStep' expressions within the call() function.
 */
object ProgressTracker {
    object GENERATING_TRANSACTION : Step("Generating transaction based on new cards.")
    object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
    object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
    object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
        override fun childProgressTracker() = CollectSignaturesFlow.tracker()
    }
    object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
        override fun childProgressTracker() = FinalityFlow.tracker()
    }

    @JvmStatic fun tracker() = ProgressTracker(
            GENERATING_TRANSACTION,
            VERIFYING_TRANSACTION,
            SIGNING_TRANSACTION,
            GATHERING_SIGS,
            FINALISING_TRANSACTION)
}
