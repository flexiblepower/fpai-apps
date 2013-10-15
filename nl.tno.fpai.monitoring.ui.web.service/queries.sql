delete from `ts`.`dim_time`;
alter table `ts`.`dim_time` auto_increment = 1;

call populate_dim_time();
select * from dim_time limit 10000;

call populate_dim_date();
select * from dim_date limit 10000;

update dim_date set quarter = floor((`month`-1) / 3 + 1);

select distinct floor((`month`-1) / 3 + 1) as quarter, `month` from dim_date;

describe dim_time;
analyze table dim_time;
analyze table dim_date;

show indexes from dim_time;
show indexes from dim_date;

select minute, avg(temperature)
	from `fact_nl.tno.fpai.logging.example.heatpump`
	join dim_time on timeId = dim_time.id
group by minute;

explain
select
	day_of_week,
	avg(temperature),
	std(temperature)
from
	`fact_nl.tno.fpai.logging.example.heatpump`
	join dim_date on dateId = dim_date.id
	join dim_observer on observerId = observer.id
where 
	month = 1
group by day_of_week;

select
	timestamp,
	temperature
from
	`fact_nl.tno.fpai.logging.example.heatpump`
	join dim_date on dateId = dim_date.id
where 
	(day_of_week = 1 or day_of_week = 7)
	and month = 1;




select
	timestamp,
	temperature
from
	`fact_nl.tno.fpai.logging.example.heatpump`
	join dim_observer on observerId = dim_observer.id
where
	observationOf = "heatpump-yyy" and
	timestamp < "2013-01-01 12:00";



select
	day_of_week,
	avg(temperature)
from
	`fact_nl.tno.fpai.logging.example.heatpump`
	join dim_date on dateId = dim_date.id
	join dim_time on timeId = dim_time.id
	join dim_observer on observerId = dim_observer.id
where 
	month = 1
	and hour > 8 and hour < 20
group by
	day_of_week;







explain
select id
from dim_date 
where (day_of_week = 1 or day_of_week = 7) and month = 1;

select
	floor(temperature) temperature,
	count(floor(temperature)) as count
from
	`fact_nl.tno.fpai.logging.example.heatpump`
group by
	floor(temperature);

select floor(temperature) temperature, count(floor(temperature)) as count from `fact_nl.tno.fpai.logging.example.heatpump` group by floor(temperature);


select avg(temperature)
	from `fact_nl.tno.fpai.logging.example.heatpump`
where dateId in (select id from dim_date where day_of_week = 1);

analyze table dim_time;

select count(*),max(timestamp) 
	from `fact_nl.tno.fpai.logging.example.heatpump`;

show table status like 'dim_time';

call `ts`.`fill_fact_n.t.f.l.e.heatpump`();