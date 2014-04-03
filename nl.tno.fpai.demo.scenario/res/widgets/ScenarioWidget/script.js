$(window).load(function() {
	var dropdown = $("#scenarios select");
	
	w = new widget("getStatus", 1000, function(data) {
		$("p").show();
		$("#loading").hide();
		$(".error").hide();
		
		$("#status").text(data.text);
	});

	w.call("getNames", {}, function(data) {
		dropdown.append($.map(data, function(v,k) {
			return $("<option>").val(v).text(v);
		}));
	});
	
	$("#start_scenarion_button").click(function() {
		w.call("startScenario", dropdown.val(), function() {
			w.update();
		});
	});
});