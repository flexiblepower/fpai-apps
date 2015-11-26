$(window).load(function() {
    w = new widget("update", 1000, function(data) {
        $("#loading").detach();
        $("p").show();
        $(".error").hide();
        $("#c_temp").val(data.mCurrentTemperature).trigger('change');

        if (data.mMode == "ONLINE") {
            $(".offline").hide();
            $(".online").show();
        } else {
            $(".offline").show();
            $(".online").hide();
        }
    });

    $("#set_light_on_btn").click(function() {
        w.call("setLightOn", {}, w.callback);
    });

    $("#set_light_off_btn").click(function() {
        w.call("setLightOff", {}, w.callback);
    });
});
