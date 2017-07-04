package com.clinicalclaims.flow

import co.paralleluniverse.fibers.Suspendable
import com.clinicalclaims.contract.ClaimContract
import com.clinicalclaims.state.ClaimState
import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.Party
import net.corda.core.crypto.composite
import net.corda.core.crypto.signWithECDSA
import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.linearHeadsOfType
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.flows.FinalityFlow

/**
 * Created by Gavan on 11/4/2017.
 */
object ProcessClaimFlow {
    class Initiator(val claimId: String,
                    val policyId: String) : FlowLogic<SignedTransaction>() {

        /** A [Policy] repreents the actual policy data */
        data class Policy(val id: String, val totalCoverage: Float, val coverageUsed: Float)

        companion object {
            class QUERYING(val policyId: String) : ProgressTracker.Step("Querying Oracle for Policy $policyId")
            object PROCESSING : ProgressTracker.Step("Processing claim based on policy information")
            object APPROVING : ProgressTracker.Step("Approving Claim.")
            object REJECTING : ProgressTracker.Step("Rejecting Claim")
            object UPDATING_ORACLE : ProgressTracker.Step("Updating Oracle with result")

            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on updated Claim.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SENDING_TRANSACTION : ProgressTracker.Step("Sending updated claim transaction to clinic.")

            fun tracker(policyId: String) = ProgressTracker(
                    QUERYING(policyId),
                    PROCESSING,
                    APPROVING,
                    REJECTING,
                    UPDATING_ORACLE,
                    GENERATING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SENDING_TRANSACTION
            )
        }

        override val progressTracker = tracker(policyId)

        @Suspendable
        override fun call(): SignedTransaction {
            //prep

            //obtain a reference to our keypair
            val keyPair = serviceHub.legalIdentityKey
            //obtain a reference to the notary
            val notary = serviceHub.networkMapCache.notaryNodes.single().notaryIdentity

            //Obtain a reference to the state in our vault
            val idx = serviceHub.vaultService.linearHeadsOfType<ClaimState>().values.indexOfFirst {
                it.state.data.claim.claimId == claimId
            }

            val oldState = serviceHub.vaultService.linearHeadsOfType<ClaimState>().values.elementAt(idx)

            //step 1 - query for policy information
            progressTracker.currentStep = QUERYING(policyId)
            //dummy policy data
            var policy = Policy(policyId, 10000.toFloat(), 0.toFloat())

            //business logic for approving or rejecting claim
            progressTracker.currentStep = PROCESSING
            val coverageRemaining = policy.totalCoverage - policy.coverageUsed

            //approve claim
            if (coverageRemaining - oldState.state.data.claim.cost >= 0) {
                progressTracker.currentStep = APPROVING
                val newState = oldState.state.data.approveClaim()

                progressTracker.currentStep = GENERATING_TRANSACTION
                //generate transaction
                val txCommand = Command(ClaimContract.Commands.Approve(), oldState.state.data.participants)
                val unsignedTx = TransactionType.General.Builder(notary).withItems(oldState, txCommand, newState)

                progressTracker.currentStep = VERIFYING_TRANSACTION
                // Verify that the transaction is valid.
                unsignedTx.toWireTransaction().toLedgerTransaction(serviceHub).verify()

                progressTracker.currentStep = SIGNING_TRANSACTION
                val partSignedTx = unsignedTx.signWith(keyPair).toSignedTransaction(checkSufficientSignatures = false)

                progressTracker.currentStep = SENDING_TRANSACTION
                // sending transaction to other party
                send(oldState.state.data.clinic, partSignedTx)

                progressTracker.currentStep = UPDATING_ORACLE

                return waitForLedgerCommit(partSignedTx.id)
            }

            //reject claim
            else {
                progressTracker.currentStep = REJECTING
                val newState = oldState.state.data.rejectClaim()

                progressTracker.currentStep = GENERATING_TRANSACTION
                //generate transaction
                val txCommand = Command(ClaimContract.Commands.Approve(), oldState.state.data.participants)
                val unsignedTx = TransactionType.General.Builder(notary).withItems(oldState, txCommand, newState)

                progressTracker.currentStep = VERIFYING_TRANSACTION
                // Verify that the transaction is valid.
                unsignedTx.toWireTransaction().toLedgerTransaction(serviceHub).verify()

                progressTracker.currentStep = SIGNING_TRANSACTION
                val partSignedTx = unsignedTx.signWith(keyPair).toSignedTransaction(checkSufficientSignatures = false)

                progressTracker.currentStep = SENDING_TRANSACTION
                // sending transaction to other party
                send(oldState.state.data.clinic, partSignedTx)

                progressTracker.currentStep = UPDATING_ORACLE

                return waitForLedgerCommit(partSignedTx.id)
            }
        }
    }

    class Acceptor(val otherParty : Party) : FlowLogic<ProcessClaimFlowResult>(){

        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object RECEIVEING_TRANSACTION : ProgressTracker.Step("Receiving Transaction from Insurer.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object FINALISING_TRANSACTION : ProgressTracker.Step("Sending fully signed transaction back to Insurer.")

            fun tracker() = ProgressTracker(
                    RECEIVEING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * Define the initiator's flow logic here.
         */
        @Suspendable
        override fun call() : ProcessClaimFlowResult {

            try {
                // Prep.
                // Obtain a reference to our key pair. Currently, the only key pair used is the one which is registered
                // with the NetWorkMapService. In a future milestone release we'll implement HD key generation so that
                // new keys can be generated for each transaction.
                val keyPair = serviceHub.legalIdentityKey
                // Obtain a reference to the notary we want to use and its public key.
                val notary = serviceHub.networkMapCache.notaryNodes.single().notaryIdentity
                val notaryPubKey = notary.owningKey


                // Stage 1.
                progressTracker.currentStep = RECEIVEING_TRANSACTION
                val partSignedTx = receive<SignedTransaction>(otherParty).unwrap { partSignedTx ->
                    // Stage 2.
                    progressTracker.currentStep = VERIFYING_TRANSACTION
                    // Check that the signature of the other party is valid.
                    // Our signature and the notary's signature are allowed to be omitted at this stage as this is only
                    // a partially signed transaction.
                    val wireTx = partSignedTx.verifySignatures(keyPair.public.composite, notaryPubKey)
                    // Run the contract's verify function.
                    // We want to be sure that the agreed-upon IOU is valid under the rules of the contract.
                    // To do this we need to run the contract's verify() function.
                    wireTx.toLedgerTransaction(serviceHub).verify()
                    // We've verified the signed transaction and return it.
                    partSignedTx
                }

                // Stage 3.
                progressTracker.currentStep = SIGNING_TRANSACTION
                val mySig = keyPair.signWithECDSA(partSignedTx.id.bytes)
                // Add our signature to the transaction.
                val signedTx = partSignedTx + mySig

                // Stage 4.
                progressTracker.currentStep = FINALISING_TRANSACTION
                // FinalityFlow() notarises the transaction and records it in each party's vault.
                subFlow(FinalityFlow(signedTx, setOf(serviceHub.myInfo.legalIdentity, otherParty)))
                return ProcessClaimFlowResult.Success("Transaction id ${signedTx.id} committed to ledger.")

            }
            catch (ex : Exception){
                return ProcessClaimFlowResult.Failure(ex.message)
            }
        }
    }

    /**
     * Helper class for returning a result from the flows.
     */
    sealed class ProcessClaimFlowResult {
        class Success(val message: String?) : ProcessClaimFlowResult() {
            override fun toString(): String = "Success($message)"
        }

        class Failure(val message: String?) : ProcessClaimFlowResult() {
            override fun toString(): String = "Failure($message)"
        }
    }

}