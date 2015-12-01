$(window).load(function() {
	w = new widget("update", 1000, function(data) {
		$("#loading").detach();
		$("p").show();
		$(".error").hide();
		$("#time").text(data.dateTime);
		$("#supply").text(data.supply);
	});
});