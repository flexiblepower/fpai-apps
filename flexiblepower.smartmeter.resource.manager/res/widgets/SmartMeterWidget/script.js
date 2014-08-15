$(window).load(function() {
	w = new widget("update", 2000, function(data) {
		$("#loading").detach();
		$("p").show();
		$(".error").hide();
		if(data.error) {
			$("p").hide();
			$(".error").show();
			$(".error").text("No data received (yet)");			
		} else {
			$("p").show();
			$(".error").hide();
			$("#currentPowerConsumptionW").text(data.currentPowerConsumptionW);
			$("#currentPowerProductionW").text(data.currentPowerProductionW);
			$("#electricityConsumptionLowRateKwh").text(data.electricityConsumptionLowRateKwh);
			$("#electricityConsumptionNormalRateKwh").text(data.electricityConsumptionNormalRateKwh);
			$("#electricityProductionLowRateKwh").text(data.electricityProductionLowRateKwh);
			$("#electricityProductionNormalRateKwh").text(data.electricityProductionNormalRateKwh);
			$("#gasConsumptionM3").text(data.gasConsumptionM3);
		}
	});
	
	w.error = function(msg) {
		$("#loading").detach();
		$("p").hide();
		$(".error").show();
		$(".error").text(msg);
	}
});