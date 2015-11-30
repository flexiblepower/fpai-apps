$(window).load(function() {
	w = new widget("update", 1000, function(data) {
		$("#loading").detach();
		$("p").show();
		$(".error").hide();
	});
	
	$("#set_demand").click(function () 
	{
		w.call("setDemand", $("#txtDemand").val(), w.callback);
    });
});