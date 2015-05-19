//$(window).load(function() {
//	w = new widget("update", 2000, function(data) {
//		$("#loading").detach();
//		$("p").show();
//		$(".error").hide();
//		$("#program").text(data.program);
//		$("#startTime").text(data.date);
//		
//		if(data.state == 4) {
//			$("#startProgram").show();
//		} else {
//			$("#startProgram").hide();
//		}
//	});
//	
//	w.error = function(msg) {
//		$("#loading").detach();
//		$("p").hide();
//		$(".error").show();
//		$(".error").text(msg);
//	}
//	
//	$("#startProgram").click(function() {
//		w.call("startProgram", {}, w.callback);
//	});
//});