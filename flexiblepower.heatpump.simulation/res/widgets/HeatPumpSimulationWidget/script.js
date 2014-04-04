$(window).load(function() {
	w = new widget("update", 1000, function(data) {
		$("#loading").detach();
		$("p").show();
		$(".error").hide();
		$("#status").text(data.status);
		$("#roomTemp").text(data.roomTemp);
		$("#minTemp").text(data.minTemp);
		$("#maxTemp").text(data.maxTemp);
		$("#power").text(data.power);
	});
	
	$("#weather").click(function() {
		w.call("changeWeather", {}, w.callback);
	});
});