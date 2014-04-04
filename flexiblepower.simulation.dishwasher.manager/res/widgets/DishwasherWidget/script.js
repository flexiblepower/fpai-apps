$(window).load(function() {
	w = new widget("update", 2000, function(data) {
		$("#loading").detach();
		$("p").show();
		$(".error").hide();
		$("#program").text(data.program);
		$("#startTime").text(data.date);
		$("#mode").text(data.mode);
	});
	
	w.error = function(msg) {
		$("#loading").detach();
		$("p").hide();
		$(".error").show();
		$(".error").text(msg);
	}
	
	$("#start_program").click(function() {
		w.call("startSimulation", {}, w.callback);
	});
	
	$("#icon").click(function() {
		w.call("startSimulation", {}, w.callback);
	});
	
});