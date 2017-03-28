package com.clinicalclaims.flow

import co.paralleluniverse.fibers.Suspendable
import com.clinicalclaims.contract.ClaimContract
import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.SignedTransaction
import com.clinicalclaims.state.ClaimState
import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.Party
import net.corda.core.crypto.composite
import net.corda.core.crypto.signWithECDSA
import net.corda.core.node.services.linearHeadsOfType
import net.corda.core.utilities.ProgressTracker
import net.corda.flows.FinalityFlow
import net.corda.core.utilities.unwrap


/**
 * Created by Gavan on 23/2/2017.
 *
 * This flow allows for an insurance company [Initiator] to approve a currently pending claim and notify the [Reciever]
 * of the updated claim status
 */
object RejectClaimFlow {

    class Initiator(val claimId: String) : FlowLogic<SignedTransaction>(){

        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on updated Claim.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SENDING_TRANSACTION : ProgressTracker.Step("Sending updated claim transaction to clinic.")

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    SENDING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * Define the initiator's flow logic here.
         */
        @Suspendable
        override fun call() : SignedTransaction {

                // Prep.
                // Obtain a reference to our key pair. Currently, the only key pair used is the one which is registered
                // with the NetWorkMapService. In a future milestone release we'll implement HD key generation so that
                // new keys can be generated for each transaction.
                val keyPair = serviceHub.legalIdentityKey
                val notary = serviceHub.networkMapCache.notaryNodes.single().notaryIdentity

                //Obtain a reference to the state in our vault
                val claims = serviceHub.vaultService.currentVault.statesOfType<ClaimState>()
                val idx = serviceHub.vaultService.linearHeadsOfType<ClaimState>().values.indexOfFirst{
                    it.state.data.claim.claimId == claimId
                }

//                //if not state return error
//                if (idx < 0)
//                    return ApproveClaimFlowResult.Failure("Claim Not Found")

                val oldState = serviceHub.vaultService.linearHeadsOfType<ClaimState>().values.elementAt(idx)
                val newState = oldState.state.data.rejectClaim()

                // Stage 1.
                progressTracker.currentStep = GENERATING_TRANSACTION
                val txCommand = Command(ClaimContract.Commands.Reject(), oldState.state.data.participants)
                val unsignedTx = TransactionType.General.Builder(notary).withItems(oldState, txCommand,newState)

                // Stage 2.
                progressTracker.currentStep = VERIFYING_TRANSACTION
                // Verify that the transaction is valid.
                unsignedTx.toWireTransaction().toLedgerTransaction(serviceHub).verify()

                // Stage 3.
                progressTracker.currentStep = SIGNING_TRANSACTION
                val partSignedTx = unsignedTx.signWith(keyPair).toSignedTransaction(checkSufficientSignatures = false)

                // Stage 4.
                progressTracker.currentStep = SENDING_TRANSACTION
                // sending transaction to other party
                send(oldState.state.data.clinic, partSignedTx)

                return waitForLedgerCommit(partSignedTx.id)

        }

    }

    class Acceptor(val otherParty : Party) : FlowLogic<RejectClaimFlowResult>(){

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
        override fun call() : RejectClaimFlowResult {

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
                return RejectClaimFlowResult.Success("Transaction id ${signedTx.id} committed to ledger.")

            }
            catch (ex : Exception){
                return RejectClaimFlowResult.Failure(ex.message)
            }
        }

    }
}

/**
* Helper class for returning a result from the flows.
*/
sealed class RejectClaimFlowResult {
    class Success(val message: String?) : RejectClaimFlowResult() {
        override fun toString(): String = "Success($message)"
    }

    class Failure(val message: String?) : RejectClaimFlowResult() {
        override fun toString(): String = "Failure($message)"
    }
}