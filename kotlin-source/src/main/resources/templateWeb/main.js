var nodePort;
var apiBaseUrl;
var policyApiBaseURL = "http://localhost:4040/api/policies/"
var approveClaimApi = "http://localhost:4040/api/claims/"
var me;
var claimId = "C" + Math.floor(Math.random() * 100000) + 1 ;
$(document).ready(function(){
    $(".submit").click(function(){
        submitClaim()
    });

    $('#Tabs a').click(function (e) {
	  e.preventDefault()
	  $(this).tab('show')
	})


	// We identify the node based on its localhost port.
    nodePort = location.port;
    apiBaseURL = "http://localhost:" + nodePort + "/api/clinicalClaims/";

    $.get(apiBaseURL + "me", function(response){
    	me = response.me
    	console.log(nodePort);
    	console.log(apiBaseURL);
    	console.log(me);
    	$('.claim-value').text(claimId);
    	$('.clinic-value').text(me);
    	$('.title').text(me)
    })
    getClaims();
    
});

//helper function to add a project to Browse Projects table
function addClaimRow(claim) {
    console.log("CLAIM")
    console.log(claim)
    console.log(claim.claim.secondaryPolicyId != 0)
    secondaryPolicy = ""
    if (claim.claim.secondaryPolicyId != 0) {
        secondaryPolicy = '<div class="claim-field-row row">\
                            <div class="col-xs-3 col-xs-offset-3">\
                                <p class="field-label">Secondary Policy #:</p>\
                            </div>\
                            <div class="col-xs-3">\
                                <p class="field-value">' +
                                    claim.claim.secondaryPolicyId +
                                '</p>\
                            </div>\
                        </div>'
    }
	if (claim.status =="Pending") {
		var container = "pending-claim";
		var list = ".pending-claims-list";
		var buttons = '<div class="claim-buttons-row row">\
							<div class="col-xs-4 col-xs-offset-4">\
								<p class="process-button" onClick="processClaim(\''+claim.claim.policyId+'\',\''+claim.claim.secondaryPolicyId+'\',\''+claim.claim.claimId+'\','+claim.claim.cost+')">Process</p>\
							</div>\
						</div>';
	}
	else if ( claim.status == "Approved"){
		var container = "approved-claim";
		var list = ".approved-claims-list";
		var buttons =""
	}
	else {
		var container = "rejected-claim";
		var list = ".rejected-claims-list";
		var buttons =""
	}

    if (location.pathname.indexOf('insurer') <0)
        buttons = "";

		var _html = '<div class="claim-row row">\
						<div class="col-xs-8 col-xs-offset-2 claim ' + container + '">\
							<div class="claim-header row">\
								<div class="col-xs-3">\
									<p class="claim-id">Claim: <span class="claim-id-value">' +
										claim.claim.claimId +
									'</span></p>\
								</div>\
								<div class="col-xs-3 col-xs-offset-6">\
									<p class="claim-status">Status: <span class="claim-status-value">' +
									claim.status +
									'</span></p>\
								</div>\
							</div>\
							<div class="claim-field-row row">\
								<div class="col-xs-3 col-xs-offset-3">\
									<p class="field-label">Insurer:</p>\
								</div>\
								<div class="col-xs-3">\
									<p class="field-value">' +
										claim.insurer +
									'</p>\
								</div>\
							</div>\
							<div class="claim-field-row row">\
								<div class="col-xs-3 col-xs-offset-3">\
									<p class="field-label">Clinic:</p>\
								</div>\
								<div class="col-xs-4">\
									<p class="field-value">' +
										claim.clinic +
									'</p>\
								</div>\
							</div>\
							<div class="claim-field-row row">\
								<div class="col-xs-3 col-xs-offset-3">\
									<p class="field-label">Policy #:</p>\
								</div>\
								<div class="col-xs-3">\
									<p class="field-value">' +
										claim.claim.policyId +
									'</p>\
								</div>\
							</div>'
							+ secondaryPolicy +
							'<div class="claim-field-row row">\
								<div class="col-xs-3 col-xs-offset-3">\
									<p class="field-label">Patient Name:</p>\
								</div>\
								<div class="col-xs-3">\
									<p class="field-value">' +
										claim.claim.firstName + " " + claim.claim.lastName +
									'</p>\
								</div>\
							</div>\
							<div class="claim-field-row row">\
								<div class="col-xs-3 col-xs-offset-3">\
									<p class="field-label">Ailment:</p>\
								</div>\
								<div class="col-xs-3">\
									<p class="field-value">' +
										claim.claim.ailment +
									'</p>\
								</div>\
							</div>\
							<div class="claim-field-row row">\
								<div class="col-xs-3 col-xs-offset-3">\
									<p class="field-label">Cost(HKD):</p>\
								</div>\
								<div class="col-xs-3">\
									<p class="field-value">' +
										claim.claim.cost +
									'</p>\
								</div>\
							</div>' +
							buttons +
						'</div>\
					</div>';
		$(list).append(_html);

}

function submitClaim(){
    showLoading();
	var claim = {};
	var endpoint = "AIA/create-claim"

	claim.claimId = claimId//placeholder;
	claim.customerId = Math.floor(Math.random() * 100000) + 1; //placeholder
	claim.policyId = $('#policy').val();
	claim.secondaryPolicyId = $('#secondary-policy').val();
	claim.clinicId = 3829 //placeholder;
	claim.firstName = $('#first-name').val();
	claim.lastName = $('#last-name').val();
	claim.ailment = $('#ailment').val();
	claim.cost = $('#cost').val();

	if (claim.secondaryPolicyId.length <= 0)
	    claim.secondaryPolicyId = 0

	console.log(claim);
	$.ajax({
	   url: apiBaseURL + endpoint,
	   type: 'PUT',
	   data : JSON.stringify(claim),
	   contentType: 'application/json',
	   success: function(response) {
	     //...
	     hideLoading();
	     alert(response);
	     window.location.reload();
	   },
	   error: function(response){
	    hideLoading()
	    alert(response);
	   }
	});

}

function showLoading() {
	$('.loading').show();
}

function hideLoading() {
	$('.loading').hide();
}

//NEEDS TO BE FIXED TO BE ATOMIC
function processClaim(policyId, secondaryPolicyId, claimId, claimAmount){
    //get policy info
    $.get(policyApiBaseURL + policyId, function(policy){
        console.log(policy)

        //if no policy found
        if (policy.result == false){
            rejectClaim(claimId)
            alert("Claim Rejected: Invalid Policy")
            return
        }

        //if policy1 coverage is enough
        var policy1RemainingCoverage = policy.coverageAmount - policy.coverageUsed
        if (policy1RemainingCoverage >= claimAmount) {
            approveClaim(claimId)
            approveClaimOracle(claimId, policyId, claimAmount)
            alert("Claim Approved")
            return
        }

        //if no second policy
        if (secondaryPolicyId == 0){
            rejectClaim(claimId)
            alert("Claim Rejected: Insufficient Coverage Remaining")
            return
        }

        //get second policy details
         $.get(policyApiBaseURL + secondaryPolicyId, function(secondaryPolicy){
            console.log(secondaryPolicy)

            //if no secondary policy found
            if (secondaryPolicy.result == false){
                rejectClaim(claimId)
                alert("Claim Rejected: Invalid Secondary Policy")
                return
            }

            var policy2RemainingCoverage = secondaryPolicy.coverageAmount - secondaryPolicy.coverageUsed

            //if claim amount is greater than total remaning coverage
            if (claimAmount > policy1RemainingCoverage + policy2RemainingCoverage) {
                rejectClaim(claimId)
                alert("Claim Rejected: Insufficient Coverage Remaining")
                return
            }

            //calculate value of coverage to be used from secondary policy
            var policy1Cost = policy1RemainingCoverage
            var policy2Cost = claimAmount - policy1RemainingCoverage

            //approve claim
            approveClaim(claimId)
            approveClaimOracle(claimId, policyId, policy1Cost)
            approveClaimOracle(claimId, secondaryPolicyId, policy2Cost)
            alert("Claim Approved")
         })
    })

}

function approveClaim(claimId) {
	var endpoint = "approve-claim";
	console.log(claimId);
	$.post(apiBaseURL + endpoint, {"claimId" : claimId}, function(response){
		console.log(response)
		getClaims()
	})
}

function approveClaimOracle(claimId, policyId, cost) {
    $.post(approveClaimApi, {claimId : claimId, policyId : policyId, cost: cost}, function(response){
    	    console.log(response)
    	})
}

function rejectClaim(claimId) {
	var endpoint = "reject-claim"
	$.post(apiBaseURL + endpoint, {"claimId" : claimId}, function(response){
		console.log(response)
		getClaims();
	})
}

function getClaims() {
    clearClaims();
	$.ajax({
	   url: apiBaseURL + "claims",
	   type: 'GET',
	   success: function(claims) {
	     //...
	     console.log(claims)
	     if (claims.length <1)
	        return

	     for (i=0;i<claims.length;i++) {
	        addClaimRow(claims[i].state.data);
	      }
	   }
	});
}

function clearClaims() {
    $('.pending-claims-list').html("");
    $('.approved-claims-list').html("");
    $('.rejected-claims-list').html("");
}