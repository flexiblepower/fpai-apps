$(window).load(function() {
	w = new widget("update", 2000, function(data) {
		$(".error").hide();
		for(var agentId in data) {
			var agentName = agentId.replace(/\./g, '_');
			var div = $("#bid-"+agentName);
			if(div.size() == 0) {
				$("#bids").append('<div id="bid-'+agentName+'"><p>'+agentName+'</p><div style="width: 400px; height: 125px;"></div></div>');
				var div = $("#bid-"+agentName);
			}
			$.plot("#bid-"+agentName+" div", [ data[agentId].coordinates, 
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