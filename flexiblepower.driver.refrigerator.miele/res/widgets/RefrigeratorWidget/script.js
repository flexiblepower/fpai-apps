$(window).load(function() {
	w = new widget("update", 2000, function(data) {
		if (data.ready) {

			$("#loading").detach();
			$("p").show();
			$(".error").hide();

			$("#currentTemperature").text(data.currentTemperature);
			$("#targetTemerature").text(data.targetTemerature);

			if (data.superCool) {
				$("#icon").attr("src", "fridge_cool.png");
				$("#icon").attr("title", "Refrigerator - SuperCool on");
			} else {
				$("#icon").attr("src", "fridge.png");
				$("#icon").attr("title", "Refrigerator - SuperCool off");
			}

		}
	});

	w.error = function(msg) {
		$("#loading").detach();
		$("p").hide();
		$(".error").show();
		$(".error").text(msg);
	}
});
