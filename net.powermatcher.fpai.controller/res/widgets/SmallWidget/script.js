$(window).load(function() {
	w = new widget("update", 1000, function(data) {
		$("#loading").detach();
		$(".error").hide();
		$("#marketprice").text(data.price);
		$("#timestamp").text(data.timestamp);

		$("#agents").empty();

		for(id in data.demands){
			$("#agents").append("<p><label>"+ id +"</label> <span>" + data.demands[id] + "</span></p>");
		}

		$("p").show();
	});
	
	w.error = function(msg) {
		$("#loading").detach();
		// $("p").hide();
		$(".error").show();
		$(".error").text(msg);
	}
});