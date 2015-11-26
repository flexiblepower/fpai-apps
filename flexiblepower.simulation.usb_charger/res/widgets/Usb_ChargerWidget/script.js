$(window).load(function () {
    w = new widget("GetCurrentConsumption", 1000, function (data) {
        $("#loading").detach();
        $("p").show();
        $("#consumption").text(data.Consumption);

        if (data.Consumption == 0) {
            $('.buttons-wrap button').removeClass('active');
            $('#set_charger_off_btn').addClass('active');
        } else {
            $('.buttons-wrap button').removeClass('active');
            $('#set_charger_on_btn').addClass('active');
        }
    });

    $("#set_charger_on_btn").click(function () {
        w.call("turnChargerOn", {}, w.callback);
    });

    $("#set_charger_off_btn").click(function () {
        w.call("turnChargerOff", {}, w.callback);
    });
});

$(document).ready(function(e) {
		 $('.loading').fadeOut();
	
		$('.buttons-wrap').on('click', 'button', function(){
		$(this).siblings().removeClass('active');
		$(this).addClass('active');
		});    
});
