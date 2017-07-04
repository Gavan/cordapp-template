package com.clinicalclaims.oracle

import co.paralleluniverse.fibers.Suspendable
import com.clinicalclaims.contract.ClaimContract
import com.clinicalclaims.flow.FetchPolicyFlow
import net.corda.core.contracts.CommandData
import net.corda.core.crypto.Party
import net.corda.node.utilities.AbstractJDBCHashSet
import net.corda.node.utilities.FiberBox
import net.corda.node.utilities.JDBCHashedTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import java.math.BigDecimal
import java.net.URL
import java.security.KeyPair
import java.time.Clock
import java.time.Instant
import java.util.*
import javax.annotation.concurrent.ThreadSafe
import khttp.get
import net.corda.core.RetryableException
import net.corda.core.contracts.Command
import net.corda.core.contracts.FixOf
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.MerkleTreeException
import net.corda.core.crypto.signWithECDSA
import net.corda.core.flows.FlowLogic
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.PluginServiceHub
import net.corda.core.node.services.ServiceType
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import org.graphstream.algorithm.APSP
import java.util.function.Function

/**
 * Created by Gavan on 6/4/2017.
 */

object PolicyOracle {
    /**
     * service type
     * used to look for this oracle using the [ServiceHub]
     */
    val type = ServiceType.corda.getSubType("policy_oracle")

    /** Regiser the Plugin*/
    class Plugin : CordaPluginRegistry() {
        override val servicePlugins = listOf(Function(::Service))
    }

    /** The Service that wraps [Oracle] and handles messages/network interaction/request scrubbing. */
    class Service(val services:PluginServiceHub) : SingletonSerializeAsToken() {
        val oracle : Oracle by lazy {
            val myNodeInfo = services.myInfo
            val myIdentity = myNodeInfo.serviceIdentities(type).first()
            val mySigningKey = services.keyManagementService.toKeyPair(myIdentity.owningKey.keys)
            Oracle(myIdentity,mySigningKey,services.clock)
        }

        init {
            // Note: access to the singleton oracle property is via the registered SingletonSerializeAsToken Service.
            // Otherwise the Kryo serialisation of the call stack in the Quasar Fiber extends to include
            // the framework Oracle and the flow will crash.
            services.registerFlowInitiator(FetchPolicyFlow.PolicySignFlow::class) { FixSignHandler(it, this) }
            services.registerFlowInitiator(FetchPolicyFlow.PolicyQueryFlow::class) { FixQueryHandler(it, this) }
        }

        private class FixSignHandler(val otherParty : Party, val service : Service) : FlowLogic<Unit>(){
            @Suspendable
            override fun call() {
                val request = receive<FetchPolicyFlow.SignRequest>(otherParty).unwrap{it}
                send(otherParty, service.oracle.sign(request.ftx))
            }
        }

        private class FixQueryHandler(val otherParty: Party, val service: Service) : FlowLogic<Unit>() {
            companion object {
                object RECEIVED : ProgressTracker.Step("Received Policy request")
                object SENDING : ProgressTracker.Step("Sending Policy Response")
            }

            override val progressTracker = ProgressTracker(RECEIVED,SENDING)

            init {
                progressTracker.currentStep = RECEIVED
            }

            @Suspendable
            override fun call(): Unit {
                val request = receive<FetchPolicyFlow.QueryRequest>(otherParty).unwrap { it }
                val answer = service.oracle.query(request.query)
                progressTracker.currentStep = SENDING
                send(otherParty, answer)
            }
        }
    }

    /** A [PolicyOf] represents the query side of retrieving information about a policy: what policy ID, at what time  */
    data class PolicyOf(val policyId: String);

    /** A [Policy] repreents the actual policy data */
    data class Policy(val of: PolicyOf, val totalCoverage: Float, val coverageUsed: Float) : CommandData

    @ThreadSafe
    class Oracle(val identity: Party, private val signingKey: KeyPair, val clock: Clock) {

        /** returns the [Policy] that matches [PolicyOf] query
         *  called before transaction is initiatied
         *  TODO -> query a list of [PolicyOf] to return a list of [Policy]
         * **/
        @Suspendable
        fun query(query: PolicyOf): Policy {
            val policyId = query.policyId;

            //query
            val policyInfo = get("http://localhost:4040/api/policies/" + policyId).jsonObject
            val coverageAmount: Float = 1000.toFloat()
            val coverageUsed: Float = 0.toFloat()
            val policy = Policy(query, coverageAmount, coverageUsed)
            knownPolicies.put(policyId, policy);
            return policy
        }

        /**
         * var to hold all known polices
         * TODO rewrite to check on demand rather than store in variable
         */
        var knownPolicies: HashMap<String, Policy> = HashMap<String, Policy>()

        // TODO: can we split into two?  Fix not available (retryable/transient) and unknown (permanent)
        class UnknownPolicy(val policy: PolicyOf) : RetryableException("Unknown fix: $policy")

        fun sign(ftx: FilteredTransaction): DigitalSignature.LegallyIdentifiable {
            if (!ftx.verify()) {
                throw MerkleTreeException("Policy Oracle: Couldn't verify partial Merkle tree.")
            }

            // Performing validation of obtained FilteredLeaves.
            // check if transaction data matches our data
            fun commandValidator(elem: Command): Boolean {
                if (!(identity.owningKey in elem.signers && elem.value is Policy))
                    throw IllegalArgumentException("Oracle received unknown command (not in signers or not FetchPolicy).")
                //transaction provided policy data
                val policy = elem.value as Policy

                //get actual policy data
                val known = knownPolicies.get(policy.of.policyId)
                //compare
                if (known == null || known != policy)
                    throw UnknownPolicy(policy.of)
                return true
            }

            fun check(elem: Any): Boolean {
                return when (elem) {
                    is Command -> commandValidator(elem)
                    else -> throw IllegalArgumentException("Oracle received data of different type than expected.")
                }
            }

            val leaves = ftx.filteredLeaves
            if (!leaves.checkWithFun(::check))
                throw IllegalArgumentException("Leave Check error")

            //everything is fine so sign transaction
            return signingKey.signWithECDSA(ftx.rootHash.bytes, identity)

        }
    }
}