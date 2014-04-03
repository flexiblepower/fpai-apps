$(window).load(function() {
	w = new widget("getStatus", 1000, function(data) {
		$("p").show();
		$("#loading").hide();
		$(".error").hide();
		
		$("#status").text(data);
	});

	w.call("getNames", {}, function(data) {
		$("#scenarios").append($.map(data, function(v,k) {
			return $("<option>").val(v).text(v);
		}));
	});
	
	$("#start_scenarion_button").click(function() {
		w.call("startScenario", drowdown.val(), function() {
			w.update();
		});
	});
});