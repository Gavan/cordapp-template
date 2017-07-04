package com.clinicalclaims.model

import net.corda.core.serialization.CordaSerializable

/**
 * A simple class representing an claim.
 *
 * This is the data structure that the parties will reach agreement over. These data structures can be arbitrarily
 * complex. See https://github.com/corda/corda/blob/master/samples/irs-demo/src/main/kotlin/net/corda/irs/contract/IRS.kt
 * for a more complicated example.
 *
 * @param customerId the customer ID
 * @param policyId the policy ID
 * @param secondaryPolicyId the secondary policy ID
 * @param clinicId the clinic ID
 * @param firstName the customer's first name
 * @param lastName the customer's last name
 * @param ailment the ailment the customer has been diagnosed with
 * @param cost  the cost of the claim in cents
 */
@CordaSerializable
data class Claim (val claimId : String,
                  val customerId : String,
                  val policyId : String,
                  val secondaryPolicyId : String,
                  val clinicId : String,
                  val firstName : String,
                  val lastName : String,
                  val ailment : String,
                  val cost : Int)