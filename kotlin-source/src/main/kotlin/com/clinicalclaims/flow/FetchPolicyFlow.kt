package com.clinicalclaims.flow

import co.paralleluniverse.fibers.Suspendable
import com.clinicalclaims.oracle.PolicyOracle
import com.clinicalclaims.oracle.PolicyOracle.Policy
import com.clinicalclaims.oracle.PolicyOracle.PolicyOf
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

/**
 * Created by Gavan on 10/4/2017.
 */
open class FetchPolicyFlow(protected val tx : TransactionBuilder,
                           protected val policyOf : PolicyOf,
                           override val progressTracker: ProgressTracker = FetchPolicyFlow.tracker(policyOf.policyId)
                           ) :FlowLogic<Unit>() {
    companion object {
        class QUERYING(val policyId: String) : ProgressTracker.Step("Querying oracle for $policyId policy")
        object WORKING : ProgressTracker.Step("Working with data returned by oracle")
        object SIGNING : ProgressTracker.Step("Requesting confirmation signature from policy oracle")

        fun tracker(policyName: String) = ProgressTracker(QUERYING(policyName), WORKING, SIGNING)
    }

    val oracleObj = serviceHub.networkMapCache.getNodesWithService(PolicyOracle.type).first()
    val oracle = oracleObj.serviceIdentities(PolicyOracle.type).first()

    @CordaSerializable
    class InvalidPolicy(@Suppress("unused") val policyId :String) :Exception ("No Policy found for Policy ID : $policyId")

    @CordaSerializable
    data class QueryRequest(val query: PolicyOf)

    @CordaSerializable
    data class SignRequest(val ftx:FilteredTransaction)

    @Suspendable
    override fun call() {
        progressTracker.currentStep = progressTracker.steps[1]
        val policy = subFlow(PolicyQueryFlow(policyOf, oracle))
        progressTracker.currentStep = WORKING
        tx.addCommand(policy, oracle.owningKey)
        beforeSigning(policy)
        progressTracker.currentStep = SIGNING
        val mtx = tx.toWireTransaction().buildFilteredTransaction ({ filtering(it) })
        val signature = subFlow(PolicySignFlow(tx,oracle,mtx))
        tx.addSignatureUnchecked(signature)
    }

    /**
     * You can override this to perform any additional work needed after the fix is added to the transaction but
     * before it's sent back to the oracle for signing (for example, adding output states that depend on the fix).
     */
    @Suspendable
    protected open fun beforeSigning(policy: Policy) {
    }

    /**
     * Filtering functions over transaction, used to build partial transaction with partial Merkle tree presented to oracle.
     * When overriding be careful when making the sub-class an anonymous or inner class (object declarations in Kotlin),
     * because that kind of classes can access variables from the enclosing scope and cause serialization problems when
     * checkpointed.
     */
    @Suspendable
    protected open fun filtering(elem: Any): Boolean = false

    class PolicyQueryFlow(val policyOf : PolicyOf, val oracle : Party) : FlowLogic<Policy>() {
        @Suspendable
        override fun call(): Policy {
            val resp = sendAndReceive<Policy>(oracle, QueryRequest(policyOf))

            return resp.unwrap {
                val policy = it
                //check the returned policy
                check(policy.of == policyOf)
                policy
            }
        }
    }

    class PolicySignFlow(val tx:TransactionBuilder, val oracle : Party,
                         val partialMerkleTx:FilteredTransaction) : FlowLogic<DigitalSignature.LegallyIdentifiable>() {
        @Suspendable
        override fun call(): DigitalSignature.LegallyIdentifiable {
            val resp = sendAndReceive<DigitalSignature.LegallyIdentifiable>(oracle,SignRequest(partialMerkleTx))

            return resp.unwrap {sig ->
                check(sig.signer == oracle)
                tx.checkSignature(sig)
                sig
            }
        }
    }

}