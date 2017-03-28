package com.clinicalclaims.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * Created by Gavan on 10/2/2017.
 * Database schema for a claim
 *
 * @param claimId the claim ID
 * @param customerId the customer ID
 * @param policyId the policy ID
 * @param clinicId the clinic ID
 * @param firstName the customer's first name
 * @param lastName the customer's last name
 * @param ailment the ailment the customer has been diagnosed with
 * @param cost  the cost of the claim in cents
 * @param status the status of the claim
 */

object ClaimSchema

object ClaimSchemaV1 : MappedSchema(
        schemaFamily = ClaimSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentClaim::class.java)) {
    @Entity
    @Table(name = "claim_states")
    class PersistentClaim(
            @Column(name = "claimId")
            var claimId : String,

            @Column(name = "customerId")
            var customerId : String,

            @Column(name = "policyId")
            var policyId : String,

            @Column(name = "clinicId")
            var clinicId : String,

            @Column(name = "firstName")
            var firstName : String,

            @Column(name = "lastName")
            var lastName : String,

            @Column(name = "ailment")
            var ailment : String,

            @Column(name = "cost")
            var cost : Int,

            @Column(name = "status")
            var status : String
    ) : PersistentState()
}