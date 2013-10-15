var margin = {top: 20, right: 20, bottom: 30, left: 50};
var width = 1240 - margin.left - margin.right;
var height = 500 - margin.top - margin.bottom;

var parseDate = d3.time.format("%Y-%m-%d %H:%M:%S.%L").parse;

var svg = d3.select("#chart-area")
	.append("svg")
		.attr("id", "chart")
		.attr("width", width + margin.left + margin.right)
		.attr("height", height + margin.top + margin.bottom)
	.append("g")
		.attr("transform", "translate(" + margin.left + "," + margin.top + ")");

function chartQuery(query, chartType) {
	var url = "sql?q=" + encodeURIComponent(query);
	
	d3.select("#data-url")
		.text(null)
		.append("a")
		.attr("href", url)
		.attr("target", "_blank")
		.text(url.length < 50 ? url : (url.substring(0,50)+" ..."));
	
	d3.tsv(url,	function(error, data) {
		d3.selectAll("svg g *").remove();

		if(data.length == 0){
			console.log("empty dataset");
			return;
		}
		
		var keys = parseKeys(data);
		var isTimeseries = parseData(keys, data);
		
		var x;
		if(chartType == "bar") {
			x = d3.scale.ordinal()
		    	.rangeBands([0, width], .5);
		} else if(isTimeseries){
			x = d3.time.scale()
	    		.range([0, width]);
		} else {
			x = d3.scale.linear()
				.range([0, width]);
		}

		var y = d3.scale.linear()
			.range([height, 0]);

		var xAxis = d3.svg.axis()
			.scale(x)
			.orient("bottom");

		var yAxis = d3.svg.axis()
			.scale(y)
			.orient("left");

		svg.append("g")
			.attr("class", "x axis")
			.attr("transform", "translate(0," + height + ")")
			.call(xAxis);

		svg.append("g")
			.attr("class", "y axis")
			.call(yAxis);

		y.domain(d3.extent(data, function(d) { return d[keys[1]]; }));

		if(chartType == "bar") {
			x.domain(data.map(function(d) { return d[keys[0]]; }));
			
			svg.selectAll(".bar")
		      .data(data)
		    .enter().append("rect")
		      .attr("class", "bar")
		      .attr("x", function(d) { return x(d[keys[0]]); })
		      .attr("y", function(d) { return y(d[keys[1]]); })
		      .attr("width", x.rangeBand())
		      .attr("height", function(d) { return height - y(d[keys[1]]); });
		} else {
			x.domain(d3.extent(data, function(d) { return d[keys[0]]; }));

			var line = d3.svg.line()
				.x(function(d) { return x(d[keys[0]]); })
				.y(function(d) { return y(d[keys[1]]); });
			
			svg.append("path")
					.datum(data)
					.attr("class", "line")
					.attr("d", line);
		}
	});
}

function parseKeys(data){
	var keys = [];
	for(var k in data[0]) {
		keys.push(k);
	}
	return keys;
}

function parseData(keys, data) {
	var isTimeseries = keys[0] == "timestamp";
	var firstNumberIdx = isTimeseries ? 1 : 0;
	
	data.forEach(function(d) {
		if(isTimeseries){
			d["timestamp"] = parseDate(d.timestamp);
		}
		
		for(var i = 1; i < keys.length; i++){
			d[keys[i]] = +d[keys[i]];
		}
	});
	
	return isTimeseries;
}

