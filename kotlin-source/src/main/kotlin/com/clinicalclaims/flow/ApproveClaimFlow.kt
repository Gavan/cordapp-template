package com.clinicalclaims.flow

import co.paralleluniverse.fibers.Suspendable
import com.clinicalclaims.contract.ClaimContract
import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.SignedTransaction
import com.clinicalclaims.state.ClaimState
import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionType
import net.corda.core.utilities.ProgressTracker

/**
 * Created by Gavan on 23/2/2017.
 *
 * This flow allows for an insurance company [Initiator] to approve a currently pending claim and notify the [Reciever]
 * of the updated claim status
 */
object ApproveClaimFlow {

    class Initiator(val claim: ClaimState) : FlowLogic<Unit>(){

        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on updated Claim.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object FINALIZING_TRANSACTION : ProgressTracker.Step("Sending updated claim transaction to clinic.")

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    FINALIZING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * Define the initiator's flow logic here.
         */
        @Suspendable
        override fun call() {
            // Prep.
            // Obtain a reference to our key pair. Currently, the only key pair used is the one which is registered
            // with the NetWorkMapService. In a future milestone release we'll implement HD key generation so that
            // new keys can be generated for each transaction.
            val keyPair = serviceHub.legalIdentityKey


            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            val txCommand = Command(ClaimContract.Commands.Approve(), claim.insurer.owningKey)
            val unsignedTx = TransactionType.General.Builder(null).withItems(claim, txCommand)
        }

    }
}