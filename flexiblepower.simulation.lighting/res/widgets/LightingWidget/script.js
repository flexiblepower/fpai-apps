$(window).load(function() {
	w = new widget("update", 2000, function(data) {
		$("#loading").detach();
		$("p").show();
		$(".error").hide();
		var demand = data.demand;
		if(demand == 0) {
			$("#icon").attr("src", "light-off.png");
		} else {
			$("#icon").attr("src", "light-on.png");
		}
		$("#power").text(demand);
		$("#numberOfLights").text(data.numberOfLights);
		
		
		
		if(data.timeSunUp) {
			$("#sunUp").text(data.timeSunUp);
		} else {
			$("#sunUp").text("");
		}
		if(data.timeSunDown) {
			$("#sunDown").text(data.timeSunDown);
		} else {
			$("#sunDown").text("");
		}
	});
});
