$(window).load(function() {
	w = new widget("update", 1000, function(data) {
		$("#loading").detach();
		$("p").show();
		$(".error").hide();
			
		$("#supply").text(data.supply);
	});
	
	$("#set_price").click(function () 
	{
	
        w.call("setPrice", $("#txtPrice").val(), w.callback);
    });
});