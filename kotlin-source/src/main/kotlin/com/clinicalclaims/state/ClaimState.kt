package com.clinicalclaims.state

import com.clinicalclaims.contract.ClaimContract
import com.clinicalclaims.model.Claim
import com.clinicalclaims.schema.ClaimSchemaV1
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey

/**
 * The state object representing a clinical claim.
 *
 * A state must implement [ContractState] or one of its descendants.
 *
 * @param claim details of the claim.
 * @param sender the party submitting the claim.
 * @param recipient the party receiving and approving the claim.
 * @param contract the contract which governs which transactions are valid for this state object.
 */
@CordaSerializable
data class ClaimState(val claim : Claim,
                 val clinic : Party,
                 val insurer : Party,
                 override val contract:ClaimContract,
                 val status : String = "Pending",
                 override val linearId : UniqueIdentifier = UniqueIdentifier()) :
    LinearState, QueryableState {

    /** The public keys of the involved parties. */
    override val participants: List<CompositeKey>
        get() = listOf(clinic,insurer).map {it.owningKey}

    /** Tells the vault to track a state if we are one of the parties involved. */
    override fun isRelevant(ourKeys: Set<PublicKey>) = ourKeys.intersect(participants.flatMap {it.keys}).isNotEmpty()

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is ClaimSchemaV1 -> ClaimSchemaV1.PersistentClaim(
                    claimId = this.claim.claimId,
                    customerId = this.claim.customerId,
                    policyId = this.claim.policyId,
                    secondaryPolicyId = this.claim.secondaryPolicyId,
                    clinicId = this.claim.clinicId,
                    firstName = this.claim.firstName,
                    lastName = this.claim.lastName,
                    ailment = this.claim.ailment,
                    cost = this.claim.cost,
                    status = this.status
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    fun withoutStatus() = copy(status = "")
    fun approveClaim() = copy(status = "Approved")
    fun rejectClaim() = copy(status = "Rejected")

    override fun supportedSchemas() : Iterable<MappedSchema> = listOf(ClaimSchemaV1)
}