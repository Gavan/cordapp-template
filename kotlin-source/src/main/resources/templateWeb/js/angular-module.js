"use strict";

// --------
// WARNING:
// --------

// THIS CODE IS ONLY MADE AVAILABLE FOR DEMONSTRATION PURPOSES AND IS NOT SECURE!
// DO NOT USE IN PRODUCTION!

// FOR SECURITY REASONS, USING A JAVASCRIPT WEB APP HOSTED VIA THE CORDA NODE IS
// NOT THE RECOMMENDED WAY TO INTERFACE WITH CORDA NODES! HOWEVER, FOR THIS
// PRE-ALPHA RELEASE IT'S A USEFUL WAY TO EXPERIMENT WITH THE PLATFORM AS IT ALLOWS
// YOU TO QUICKLY BUILD A UI FOR DEMONSTRATION PURPOSES.

// GOING FORWARD WE RECOMMEND IMPLEMENTING A STANDALONE WEB SERVER THAT AUTHORISES
// VIA THE NODE'S RPC INTERFACE. IN THE COMING WEEKS WE'LL WRITE A TUTORIAL ON
// HOW BEST TO DO THIS.

const app = angular.module('demoAppModule', ['ui.bootstrap']);

// Fix for unhandled rejections bug.
app.config(['$qProvider', function ($qProvider) {
    $qProvider.errorOnUnhandledRejections(false);
}]);

app.controller('DemoAppController', function($http, $location, $uibModal) {
    const demoApp = this;

    // We identify the node based on its localhost port.
    const nodePort = $location.port();
    const apiBaseURL = "http://localhost:" + nodePort + "/api/clinicalClaims/";
    let peers = [];

    $http.get(apiBaseURL + "me").then((response) => demoApp.thisNode = response.data.me);

    $http.get(apiBaseURL + "peers").then((response) => peers = response.data.peers);

    demoApp.openModal = () => {
        const modalInstance = $uibModal.open({
            templateUrl: 'demoAppModal.html',
            controller: 'ModalInstanceCtrl',
            controllerAs: 'modalInstance',
            resolve: {
                apiBaseURL: () => apiBaseURL,
                peers: () => peers
            }
        });

        modalInstance.result.then(() => {}, () => {});
    };

    demoApp.getClaims = () => $http.get(apiBaseURL + "claims")
        .then((response) => demoApp.claims = Object.keys(response.data)
            .map((key) => response.data[key].state.data)
            .reverse());

    demoApp.getClaims();
});

app.controller('ModalInstanceCtrl', function ($http, $location, $uibModalInstance, $uibModal, apiBaseURL, peers) {
    const modalInstance = this;

    modalInstance.peers = peers;
    modalInstance.form = {};
    modalInstance.formError = false;

    // Validate and create Claim.
    modalInstance.create = () => {
        if (invalidFormInput()) {
            modalInstance.formError = true;
        } else {
            modalInstance.formError = false;

            const claim = {
                claimId: modalInstance.form.claimId,
                customerId: modalInstance.form.customerId,
                policyId: modalInstance.form.policyId,
                clinicId: modalInstance.form.clinicId,
                firstName: modalInstance.form.firstName,
                lastName: modalInstance.form.lastName,
                ailment: modalInstance.form.ailment,
                cost: modalInstance.form.cost,
            };

            $uibModalInstance.close();

            const createClaimEndpoint =
                apiBaseURL +
                modalInstance.form.otherParty +
                "/create-claim";

            // Create PO and handle success / fail responses.
            $http.put(createClaimEndpoint, angular.toJson(claim)).then(
                (result) => modalInstance.displayMessage(result),
                (result) => modalInstance.displayMessage(result)
            );
        }
    };

    modalInstance.displayMessage = (message) => {
        const modalInstanceTwo = $uibModal.open({
            templateUrl: 'messageContent.html',
            controller: 'messageCtrl',
            controllerAs: 'modalInstanceTwo',
            resolve: { message: () => message }
        });

        // No behaviour on close / dismiss.
        modalInstanceTwo.result.then(() => {}, () => {});
    };

    // Close create Claim modal dialogue.
    modalInstance.cancel = () => $uibModalInstance.dismiss();

    // Validate the Claim.
    function invalidFormInput() {
        return false;
    }
});

// Controller for success/fail modal dialogue.
app.controller('messageCtrl', function ($uibModalInstance, message) {
    const modalInstanceTwo = this;
    modalInstanceTwo.message = message.data;
});