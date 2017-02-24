package com.clinicalclaims.plugin

import com.esotericsoftware.kryo.Kryo
import com.clinicalclaims.api.ClaimApi
import com.clinicalclaims.contract.ClaimContract
import com.clinicalclaims.flow.CreateClaimFlow
import com.clinicalclaims.model.Claim
import com.clinicalclaims.service.ClaimService
import com.clinicalclaims.state.ClaimState
import net.corda.core.contracts.AuthenticatedObject
import net.corda.core.contracts.Timestamp
import net.corda.core.contracts.TransactionType
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.PluginServiceHub
import net.corda.core.transactions.LedgerTransaction
import java.util.function.Function

class ClaimPlugin : CordaPluginRegistry() {
    /**
     * A list of classes that expose web APIs.
     */
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::ClaimApi))

    /**
     * A list of flows required for this CorDapp.
     */
    override val requiredFlows: Map<String, Set<String>> = mapOf(
            CreateClaimFlow.Initiator::class.java.name to setOf(ClaimState::class.java.name, Party::class.java.name)
    )

    /**
     * A list of long-lived services to be hosted within the node.
     */
    override val servicePlugins: List<Function<PluginServiceHub, out Any>> = listOf(Function(ClaimService::Service))

    /**
     * A list of directories in the resources directory that will be served by Jetty under /web.
     * The clinicalclaims's web frontend is accessible at /web/clinicalClaims.
     */
    override val staticServeDirs: Map<String, String> = mapOf(
            // This will serve the templateWeb directory in resources to /web/clinicalClaims
            "clinicalClaims" to javaClass.classLoader.getResource("templateWeb").toExternalForm()
    )

    /**
     * Registering the required types with Kryo, Corda's serialisation framework.
     */
    override fun registerRPCKryoTypes(kryo: Kryo): Boolean {
        kryo.register(ClaimState::class.java)
        kryo.register(ClaimContract::class.java)
        kryo.register(Claim::class.java)
        kryo.register(TransactionVerificationException.ContractRejection::class.java)
        kryo.register(LedgerTransaction::class.java)
        kryo.register(AuthenticatedObject::class.java)
        kryo.register(ClaimContract.Commands.Create::class.java)
        kryo.register(Timestamp::class.java)
        kryo.register(TransactionType.General::class.java)
        return true
    }
}