$(window).load(function() {
	w = new widget("update", 2000, function(data) {
		$(".error").hide();
		for(var agentId in data) {
			var div = $("#bid-"+agentId);
			if(div.size() == 0) {
				$("#bids").append('<div id="bid-'+agentId+'"><p>'+agentId+'</p><div style="width: 400px; height: 125px;"></div></div>');
				var div = $("#bid-"+agentId);
			}
			$.plot("#bid-"+agentId+" div", [ data[agentId].coordinates, 
			                                 [[data[agentId].price,-data[agentId].maxDemand],
			                                  [data[agentId].price,data[agentId].maxDemand]
			                                 ]]);
		}
	});
	
	w.error = function(msg) {
		$(".error").show();
		$(".error").text(msg);
	}
});