$(window).load(function() {
	w = new widget("update", 1000, function(data) {
		$("#loading").detach();
		$("p").show();
		$("#soc").text(data.soc.toFixed(2) + "%");
		$("#tc").text(data.initialTotalCapacity + " kWh");
		$("#mode").text(data.mode);
		$("#voltage").text(data.voltage.toFixed(2) + " V");
		$("#current").text(data.current.toFixed(2) + " A");
		$("#power").text(data.chargingPower.toFixed(2) + " W");
		$("#capacityLeft").text(data.percentageOfInitialCapacityLeft.toFixed(2) + "%")
		
		
		if(data.soc > 87) {
			$("#icon").attr("src", "8.png");
		} else if(data.soc > 75) {
			$("#icon").attr("src", "7.png");
		} else if(data.soc > 62) {
			$("#icon").attr("src", "6.png");
		} else if(data.soc > 50) {
			$("#icon").attr("src", "5.png");
		} else if(data.soc > 37) {
			$("#icon").attr("src", "4.png");
		} else if(data.soc > 25) {
			$("#icon").attr("src", "3.png");
		} else if(data.soc > 12) {
			$("#icon").attr("src", "2.png");
		} else {
			$("#icon").attr("src", "1.png");
		}
	});
});
