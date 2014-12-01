# FPAI Monitoring

This repository will host several solutions for monitoring FPAI nodes. This currently includes the following bundles:

- *flexiblepower.monitoring.csv.writer* This bundle can write all observations to CSV files. One CSV File for each ObserverProvider will be started.
- *flexiblepower.monitoring.mqtt.writer* This bundle can publish all observations to a MQTT bus. Each ObservationProvider will publish on its own topic in a JSON format.
- *flexiblepower.monitoring.mysql.writer* This bundle can write all observations that are created in the FPAI node to a mysql database using a star-scheme.
- *flexiblepower.monitoring.mysql.ui* This bundle starts a web-interface such that the database (e.g. from the mysql.writer) can be read and data can be shown in a graph.
- *net.logstash.logback* This bundle adds support for writing logging from the FPAI node to a LogStash instance. This is based on the original version, but changed to support OSGi better. See [here](net.logstash.logback/README.md) for more info.
- *flexiblepower.monitoring.dummy* This bundle adds an component that can generate random observations. This is helpful for testing purposes only.
