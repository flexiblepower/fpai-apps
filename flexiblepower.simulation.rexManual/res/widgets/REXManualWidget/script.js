$(window).load(function () {
    
    w = new widget("update", 1000, function (data) {
        $("#loading").detach();
        $("p").show();
        $(".error").hide();
    });

    $("#set_demand").click(function () {
        w.call("setDemand", $("#txtDemand").val(), w.callback);
    });

    $("#on").click(function () {
        w.call("setDemand", "-100000", w.callback);
    });
    
    $("#na").click(function () {
        w.call("setDemand", "0", w.callback);
    });

    $("#off").click(function () {
        w.call("setDemand", "100000", w.callback);
    });

    w.call("setDemand", "0", w.callback);
});