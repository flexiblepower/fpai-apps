$(window).load(function() {
	var currData = null;

	w = new widget("getNewAdresses", 5000, function(data) {
		if(JSON.stringify(data) != JSON.stringify(currData)) {
			div = ${"#detecteddevices"};
			div.empty();
			
			if(data.length <= 0) {
				div.append("<p>None detected so far</p>");
			} else {
				for(address in data) {
					div.append("<p>" + address + "</p>");
				}
			}
		}
	});
});
