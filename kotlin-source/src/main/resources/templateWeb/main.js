var nodePort;
var apiBaseUrl;
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
	if (claim.status =="Pending") {
		var container = "pending-claim";
		var list = ".pending-claims-list";
		var buttons = '<div class="claim-buttons-row row">\
							<div class="col-xs-3 col-xs-offset-2">\
								<p class="reject-button" onClick="rejectClaim(\''+claim.claim.claimId+'\')">Reject</p>\
							</div>\
							<div class="col-xs-3 col-xs-offset-2">\
								<p class="approve-button" onClick="approveClaim(\''+claim.claim.claimId+'\')">Approve</p>\
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
							</div>\
							<div class="claim-field-row row">\
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
	claim.clinicId = 3829 //placeholder;
	claim.firstName = $('#first-name').val();
	claim.lastName = $('#last-name').val();;
	claim.ailment = $('#ailment').val();;
	claim.cost = $('#cost').val();;

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
	     getClaims();
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

function approveClaim(claimId) {
	var endpoint = "approve-claim";
	console.log(claimId);
	$.post(apiBaseURL + endpoint, {"claimId" : claimId}, function(response){
		console.log(response)
		getClaims()
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