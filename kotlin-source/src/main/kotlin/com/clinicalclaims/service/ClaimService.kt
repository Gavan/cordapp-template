package com.clinicalclaims.service

import com.clinicalclaims.flow.ApproveClaimFlow
import com.clinicalclaims.flow.CreateClaimFlow
import com.clinicalclaims.flow.RejectClaimFlow
import net.corda.core.node.PluginServiceHub

/**
 * This service registers a flow factory that is used when a initiating party attempts to communicate with us
 * using a particular flow. Registration is done against a marker class (in this case, [CreateClaimFlow.Initiator])
 * which is sent in the session handshake by the other party. If this marker class has been registered then the
 * corresponding factory will be used to create the flow which will communicate with the other side. If there is no
 * mapping, then the session attempt is rejected.
 *
 * In short, this bit of code is required for the recipient in this scenario to respond to the sender using the
 * [CreateClaimFlow.Acceptor] flow.
 */
object ClaimService {
    class Service(services: PluginServiceHub) {
        init {
            services.registerFlowInitiator(CreateClaimFlow.Initiator::class) {
                CreateClaimFlow.Acceptor(it)
            }
            services.registerFlowInitiator(ApproveClaimFlow.Initiator::class){
                ApproveClaimFlow.Acceptor(it)
            }
            services.registerFlowInitiator(RejectClaimFlow.Initiator::class){
                RejectClaimFlow.Acceptor(it)
            }
        }
    }
}