package com.clinicalclaims.api

import com.clinicalclaims.contract.ClaimContract
import com.clinicalclaims.model.Claim
import com.clinicalclaims.flow.CreateClaimFlow.Initiator
import com.clinicalclaims.state.ClaimState
import net.corda.core.getOrThrow
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response


val NOTARY_NAMES = listOf("Controller", "NetworkMapService")

// This API is accessible from /api/clinicalClaims. The endpoint paths specified below are relative to it.
@Path("clinicalClaims")
class ClaimApi(val services: CordaRPCOps) {
    private val myLegalName: String = services.nodeIdentity().legalIdentity.name

    companion object {
        private val logger: Logger = loggerFor<ClaimApi>()
    }

    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers() = mapOf("peers" to services.networkMapUpdates().first
            .map { it.legalIdentity.name }
            .filter { it != myLegalName && it !in NOTARY_NAMES })

    /**
     * Displays all Claim states that exist in the node's vault.
     */
    @GET
    @Path("claims")
    @Produces(MediaType.APPLICATION_JSON)
    fun getClaims() = services.vaultAndUpdates().first

    /**
     * Initiates a flow to submit a clinical claim.
     *
     * Once the flow finishes it will have written the claim to ledger. Both the sender and the recipient will be able to
     * see it when calling /api/clinicalClaims/claims on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PUT
    @Path("{party}/create-claim")
    fun createClaim(claim: Claim, @PathParam("party") partyName: String): Response {
        val otherParty = services.partyFromName(partyName)
        if (otherParty == null) {
            return Response.status(Response.Status.BAD_REQUEST).build()
        }

        val state = ClaimState(
                claim,
                services.nodeIdentity().legalIdentity,
                otherParty,
                ClaimContract())

        val (status, msg) = try {
            // The line below blocks and waits for the future to resolve.
            val result = services
                    .startFlow(::Initiator, state, otherParty)
                    .returnValue
                    .getOrThrow()

            Response.Status.CREATED to "Transaction id ${result.id} committed to ledger."

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.Status.BAD_REQUEST to "Transaction failed."
        }

        return Response.status(status).entity(msg).build()
    }
}