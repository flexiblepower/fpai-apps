$(window).load(function() {
	w = new widget("update", 5000, function(data) {
		$("#loading").detach();
		$("p").show();
		$(".error").hide();
		$("#resource").text(data.resourceId);
		$("#demand").text(data.demandWatts);
	});
});