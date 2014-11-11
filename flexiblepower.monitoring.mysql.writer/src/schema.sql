DROP DATABASE IF EXISTS fpai_monitoring;
CREATE DATABASE fpai_monitoring;

USE fpai_monitoring;

# TABLE: fpai_monitoring.dim_date
CREATE TABLE `dim_date` (
  `date` date NOT NULL,
  `year` int(11) unsigned NOT NULL,
  `quarter` int(1) unsigned NOT NULL,
  `month` int(2) unsigned NOT NULL,
  `week` int(2) unsigned NOT NULL,
  `day_of_year` int(3) unsigned NOT NULL,
  `day_of_month` int(2) unsigned NOT NULL,
  `day_of_week` int(1) unsigned NOT NULL,
  PRIMARY KEY (`date`),
  UNIQUE KEY `date_UNIQUE` (`date`),
  KEY `day_of_week` (`day_of_week`),
  KEY `month` (`month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


# TABLE: fpai_monitoring.dim_observer
CREATE TABLE `dim_observer` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `observedBy` varchar(255) NOT NULL,
  `observationOf` varchar(255) NOT NULL,
  `type` text NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `unique_observer` (`observedBy` ASC, `observationOf` ASC)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8;


# TABLE: fpai_monitoring.dim_time
CREATE TABLE `dim_time` (
  `time` time NOT NULL,
  `hour` int(2) unsigned NOT NULL,
  `minute` int(2) unsigned NOT NULL,
  PRIMARY KEY (`time`),
  UNIQUE KEY `time_UNIQUE` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


# PROCEDURE: fpai_monitoring.populate_dim_date
delimiter $$
CREATE PROCEDURE `populate_dim_date`()
BEGIN
        declare d date default '2013-01-01';

        delete from `fpai_monitoring`.`dim_date`;
        alter table `fpai_monitoring`.`dim_date` auto_increment = 1;

        while('2021-01-01' > d) do
                INSERT INTO `fpai_monitoring`.`dim_date`
                        (`date`, `year`, `quarter`, `month`, `week`, `day_of_year`, `day_of_month`, `day_of_week`)
                VALUES
                        (d, year(d), floor((month(d)-1) / 3 + 1), month(d), week(d, 0), dayofyear(d), dayofmonth(d), dayofweek(d));

                set d = d + interval 1 day;
        end while;
END$$



# PROCEDURE: fpai_monitoring.populate_dim_time
delimiter $$
CREATE PROCEDURE `populate_dim_time`()
BEGIN
        declare h int default 0;
        declare m int default 0;

        delete from `fpai_monitoring`.`dim_time`;
        alter table `fpai_monitoring`.`dim_time` auto_increment = 1;

        ins: loop
                INSERT INTO `fpai_monitoring`.`dim_time` (`time`, `hour`, `minute`) VALUES (concat(h,":",m,":0"), h, m);

                set m = m + 1;

                if m = 60 then
                        set h = h + 1;
                        set m = 0;
                end if;

                if h = 24 then
                        leave ins;
                end if;
        end loop;
END $$

delimiter ;

call populate_dim_date();
call populate_dim_time();