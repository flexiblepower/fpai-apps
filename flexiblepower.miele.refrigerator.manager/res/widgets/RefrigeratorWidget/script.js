$(window).load(function() {
	w = new widget("update", 2000, function(data) {
		$("#loading").detach();
		$("p").show();
		$(".error").hide();
		
		$("#temp").text(data.temperature);
		$("#minTemp").text(data.minimumTemperature);
		$("#targetTemp").text(data.maximumTemperature);
		
		if(data.superCool) {
			$("#icon").attr("src", "fridge_cool.png");
			$("#icon").attr("title", "Refrigerator - SuperCool on");
		} else {
			$("#icon").attr("src", "fridge.png");
			$("#icon").attr("title", "Refrigerator - SuperCool off");
		}
	});
	
	w.error = function(msg) {
		$("#loading").detach();
		$("p").hide();
		$(".error").show();
		$(".error").text(msg);
	}
});
