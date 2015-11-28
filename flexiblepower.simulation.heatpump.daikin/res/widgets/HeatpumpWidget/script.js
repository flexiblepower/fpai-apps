$(window).load(function() {
	w = new widget("update", 1000, function(data) {
		$("#loading").detach();
		$("p").show();
		$("#temp").text(data.temp + "C");
		$("#mode").text(data.mode);
		
		$("#icon").attr("src", "1.png");
		}
	});

