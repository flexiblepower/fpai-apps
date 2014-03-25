angular.module('hexabusApp', [])
	.factory('pollingService', ['$http', '$timeout',
		function($http, $timeout) {
			var defaultPollingTime = 2000;
			var polls = {};
			
			return {
				start: function($scope, name, url, pollingTime) {
					var pollingTime = pollingTime || defaultPollingTime;
					var callback = function(data) {
						$scope[name] = data;
					};
					if(!polls[name]) {
						var func = function() {
							$http.get(url).success(callback);
							polls[name] = $timeout(func, pollingTime);
						}
						polls[name] = $timeout(func, 1);
					}
				},
				
				stop: function(name) {
					if(polls[name]) {
						$timeout.cancel(polls[name]);
						delete polls[name];
					}
				}
			};
		}])
	.controller('HexabusCtrl', ['$scope', '$timeout', 'pollingService', 
		function($scope, $timeout, pollingService) {
			$scope.devices = [];
			pollingService.start($scope, 'devices', 'getInfo');
		}]);
