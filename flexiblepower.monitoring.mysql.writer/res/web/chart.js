
var data, dataSource, query, chartType;

var margins = {
		area: {top: 10, right: 10, bottom: 10, left: 10},
		chart: {top: 50, right: 120, bottom: 60, left: 50}
}
var dimensions = getChartDimensions();

var parseTimestamp = d3.time.format("%Y-%m-%d %H:%M:%S.%L").parse;
var label = function(d) { return d.length > 18 ? d.substring(0,18)+"..." : d; };
var defined = function(d) { return !isNaN(d.value) && d.value != null; };

var svg = d3.select("#chart-area")
	.append("svg")
		.attr("id", "chart")
		.attr("width", dimensions.area.width + margins.area.left + margins.area.right)
		.attr("height", dimensions.area.height + margins.area.top + margins.area.bottom);
var root = svg.append("g")
	.attr("transform", "translate(" + margins.area.left + "," + margins.area.top + ")");
var chart = svg.append("g")
		.attr("transform", "translate(" + margins.chart.left + "," + margins.chart.top + ")");

function getChartDimensions() {
	var chartAreaNode = d3.select("#chart-area").node();
	
	var a = {
		width: chartAreaNode.offsetWidth - margins.area.left - margins.area.right,
		height: Math.max(200, chartAreaNode.offsetHeight) - margins.area.top - margins.area.bottom
	};
	
	var c = {
		width: a.width - margins.chart.left - margins.chart.right,
		height: a.height - margins.chart.top - margins.chart.bottom
	}
	
	return {area: a, chart: c};
}

function resizeChart() {
	dimensions = getChartDimensions();
	
	d3.select("#chart-area > svg")
		.attr("width", dimensions.area.width + margins.area.left + margins.area.right)
		.attr("height", dimensions.area.height + margins.area.top + margins.area.bottom);
	
	updateQuery(data);
}

function chartQuery(q, t) {
	query = q;
	chartType = t;
	
	var url = "sql?cmd=query&q=" + encodeURIComponent(query);
	
	d3.select("#data-url")
		.text(null)
		.append("a")
		.attr("href", url)
		.attr("target", "_blank")
		.text(url.length < 50 ? url : (url.substring(0,50)+" ..."));
	
	d3.tsv(url,	function(error, d) {
		if(error){
			alert(error.responseText.lenght < 400 ? error.responseText : (error.responseText.substring(0, 400) + "..."));
			updateQuery(d);
		} else {
			data = d;
			updateQuery(d);
		}
	});
}

function updateQuery(data) {
	d3.selectAll("svg g *").remove();

	if(!data || data.length == 0){
		console.log("empty dataset");
		return;
	}
	
	// parse the keys, and get the x key and the y keys (one for each series)
	var keys = parseKeys(data);
	var xKey = keys[0]; 
	var yKeys = keys.slice(1);
	
	// parse the series
	var isTS = isTimeSeries(xKey, data);
	var columns = parseData(xKey, yKeys, data);
	
	var x;
	if(chartType == "bar") {
		x  = d3.scale.ordinal()
    		 .rangeBands([0, dimensions.chart.width], .25);
	} else if(isTS){
		x = d3.time.scale()
    		.range([0, dimensions.chart.width]);
	} else {
		x = d3.scale.linear()
			.range([0, dimensions.chart.width]);
	}

	var xAxis = d3.svg.axis()
		.scale(x)
		.orient("bottom");
	
	var xTicks = d3.svg.axis()
		.scale(x)
		.tickSize(-dimensions.chart.height);

	var y = d3.scale.linear()
		.range([dimensions.chart.height, 0]);

	var yAxis = d3.svg.axis()
		.scale(y)
		.orient("left");
	
	// calculate min and max over all columns for y axis
	y.domain(
		d3.extent([
			0,
			1.1 * d3.min(columns.map(function(s){ return d3.min(s.values, function(d){ return d.value; })})),
			1.1 * d3.max(columns.map(function(s){ return d3.max(s.values, function(d){ return d.value; })}))
		])
	);
	
	// create a color scale for the different series
	var color = d3.scale.category10();
	color.domain(yKeys);

	if(chartType == "bar") {
		// domain of x-axis are the values of the first column
		var categories = data.map(function(d) { return d[xKey]; });
		x.domain(categories);

		// the secondary domain is built from the keys (the name of the second and subsequent columns)
		var x1 = d3.scale.ordinal();
		x1.domain(yKeys).rangeRoundBands([0, x.rangeBand()]);

		// create a group for each category
		var series = chart.selectAll(".series")
		  .data(columns)
		.enter().append("g")
	      .attr("class", "g")
	      .attr("transform", function(d) { return "translate(" + x1(d.name) + ",0)"; })
	      .style("fill", function(d) { return color(d.name); });

		// add bars for each value in each group
		series.selectAll("rect")
	      .data(function(d) { return d.values; })
	    .enter().append("rect")
	      .attr("width", x1.rangeBand())
	      .attr("x", function(d) { return x(d.key); })
	      .attr("y", function(d) { return Math.min(y(0), y(d.value)); })
	      .attr("height", function(d) { return Math.max(1, Math.abs(y(0) - y(d.value))); });
	} else {
		// the domain for the x-axis is the min and max of the first column
		x.domain(d3.extent(data, function(d) { return d[xKey]; }));

		var line = d3.svg.line()
			//.interpolate("cardinal")
			.x(function(d) { return x(d.key); })
			.y(function(d) { return y(d.value); })
			.defined(defined);

		var allLines = chart.append('g')
			.attr("class", "lines");
		
		var series = allLines.selectAll(".series")
	      .data(columns)
	    .enter().append("g")
	      .attr("class", "series");

		series.append("path")
	      .attr("class", "line")
	      .attr("d", function(d) { return line(d.values); })
	      .style("stroke", function(d) { return color(d.name); });
		
		series.append("text")
	      .datum(function(d) { 
	    	  var values = d.values.filter(defined);
	    	  return {name: d.name, value: values[0]}; 
    	  })
	      .attr("transform", function(d) { return "translate(" + x(d.name) + "," + y(d.value) + ")"; })
	      .attr("x", ".5em")
	      .attr("dy", ".35em")
	      .text(function(d) { return label(d.name); });

		if(data.length <= dimensions.area.width / 6) {
			var allDots = chart.append('g')
				.attr("class", "dots");
			
			columns.forEach(function(c) {
				var columnDots = allDots.append('g');
				
				columnDots.selectAll(".point")
		          .data(c.values.filter(defined))
		        .enter().append("circle")
					.attr("fill", function(d) { return color(c.name); })
					.attr("cx", function(d) { return x(d.key); })
					.attr("cy", function(d) { return y(d.value); })
					.attr("r", 2)
				.append("title")
				   .text(function(d) { return d.key + " = " + d.value; });
			});
		}
	}

	chart.append("g")
		.attr("class", "x axis")
		.attr("transform", "translate(0," + dimensions.chart.height + ")")
		.call(xAxis)
		.selectAll("text")
			.style("text-anchor", "end")
            .attr("dx", "-.8em")
            .attr("dy", ".15em")
			.attr("transform", "rotate(-45)");

	// add x ticks for line charts 
	if(chartType == "line") {
		chart.append("g")
			.attr("class", "x ticks")
			.attr("transform", "translate(0," + dimensions.chart.height + ")")
			.call(xTicks);
	}

	chart.append("g")
		.attr("class", "y zero")
		.attr("transform", "translate(0," + y(0) + ")")
		.call(xAxis);

	chart.append("g")
		.attr("class", "y axis")
		.call(yAxis);
	

	var legend = root.append('g')
		.attr("class", "legend");
	var legendItem = legend.selectAll(".legend.item")
      .data(yKeys)
    .enter().append("g")
      .attr("class", "legend item")
      .attr("transform", function(d, i) { return "translate(" + (i * Math.min(150, dimensions.area.width / yKeys.length)) + ",0)"; });

	legendItem.append("rect")
      .attr("x", 0)
      .attr("y", 0)
      .attr("width", "1em")
      .attr("height", "1em")
      .style("fill", color);

	legendItem.append("text")
      .attr("x", "1.5em")
      .attr("y", ".5em")
      //.attr("dy", ".35em")
      //.style("text-anchor", "end")
      .style("alignment-baseline", "middle")
      .text(label);
}

function parseKeys(data){
	return d3.keys(data[0]);
}

function isTimeSeries(xKey, data) {
	var v = data[0][xKey];
	
	// take the value for the first row 
	if(typeof v === 'string') {
		// and first key and try to parse it as a time stamp
		return parseTimestamp(v) != null;
	} else {
		return isTimeStamp(v);
	}
}

function isTimeStamp(v) {
	return typeof v.getMonth === 'function';
}

function parseData(xKey, yKeys, data) {
	// parse all time stamps, if the first column contains time stamps
	if(isTimeSeries(xKey, data)){
		data.forEach(function(d) {
			d[xKey] = isTimeStamp(d[xKey]) ? d[xKey] : parseTimestamp(d[xKey]);
		});
	} else {
		data.forEach(function(d) {
			d[xKey] = +d[xKey];
		});
	}

	// parse the series into a array of objects with name and values
	return yKeys.map(function(yKey){
		return {
			name: yKey,
			values: data.map(function(d) {
				return {
					key: d[xKey],
					value: +d[yKey]
				};
			})
		};
	});
}

