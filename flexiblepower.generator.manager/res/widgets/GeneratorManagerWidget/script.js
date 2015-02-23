$(window).load(function() {
	w = new widget("update", 1000, function(data) {
		$("#loading").detach();
		$("p").show();
		$("#level").text(data.level);
	});
	
	$("#icon").click(function() {
		w.call("changeLevel", {}, w.callback);
	});
});
