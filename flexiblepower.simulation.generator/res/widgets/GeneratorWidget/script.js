$(window).load(function() {
	w = new widget("update", 1000, function(data) {
		$("#loading").detach();
		$("p").show();
		$("#level").text(data.level);
		console.log("Updated widget");
	});
});
