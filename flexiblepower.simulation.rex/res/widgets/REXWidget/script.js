$(window).load(function() {
	w = new widget("update", 1000, function(data) {console.log(data);
		$("#loading").detach();
		$("p").show();
		$(".error").hide();
		$("#price").text(data.price);
		$("#demand").text(data.demand);
	});
});