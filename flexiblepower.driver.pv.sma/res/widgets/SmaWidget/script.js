$(window).load(function() {
	w = new widget("update", 5000, function(data) {
		$("#loading").detach();
		$("p").show();
		$(".error").hide();
		if (data) {
		    $("#power").text((-data.demand).toFixed(0));
		    $("#today").text(data.todayProduction.toFixed(2));
		    $("#lifetime").text(data.lifetimeProduction.toFixed(2));
		    $("#sunup").text(data.sunUp? "yes" : "no");
		} else {
			$("#power").text("-");
			$("#today").text("-");
			$("#lifetime").text("-");
			$("#sunup").text("-");
		}
	});
});
