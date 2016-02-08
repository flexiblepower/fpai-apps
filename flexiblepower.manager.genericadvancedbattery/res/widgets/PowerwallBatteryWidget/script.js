$(window).load(function() {
	w = new widget("update", 1000, function(data) {
		$("#loading").detach();
		$("p").show();
		$("#soc").text(data.soc.toFixed(2) + "%");
		$("#tc").text(data.initialTotalCapacity + " kWh");
		$("#mode").text(data.mode);
		$("#power").text(data.chargingPower.toFixed(2) + " W");
		
		if(data.mode.toUpperCase() === "IDLE") {
			$("#mode").removeClass("blinking");
		} else {
			$("#mode").addClass("blinking");
		}
	});
});
