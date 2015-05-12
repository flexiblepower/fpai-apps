
$(window).load(function() {
	w = new widget("update", 2000, function(data) {
		$("#loading").detach();
		$("p").show();
		$(".error").hide();
		$("#program").text(data.program);
		$("#latestStartTime").text(data.latestStartTime);
		$("#startedAt").text(data.startedAt);
		$("#icon").attr("src", "dishwasher.png");
	});
	
	w.error = function(msg) {
		$("#loading").detach();
		$("p").hide();
		$(".error").show();
		$(".error").text(msg);
	}
	
	$("#icon").click(function() {
		w.call("startProgram", {}, w.callback);
	});
});