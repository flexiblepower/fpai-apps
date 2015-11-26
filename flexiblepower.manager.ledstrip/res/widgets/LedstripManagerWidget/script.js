$(window).load(function() {
	w = new widget("update", 1000, function(data) {
		$("#loading").detach();
		$("p").show();
		$("#powervalue").val(data.level);

		var ledWatts = $('#powervalue');
		ledWatts.val(Math.round(data.level)).trigger('change')
		
	});
	
	$("#icon").click(function() {
		w.call("changeLevel", {}, w.callback);
	});
});
