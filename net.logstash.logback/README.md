# Logback JSON encoder for Logstash (FlexiblePower Version)

Provides a logback encoder, logback layout, and several logback appenders
for outputting log messages in logstash's JSON format.

This special version is made to compile using bndtools for OSGi. This works by
being a bundle-fragment of logback-classic. Also it uses gson for serialization
in stead of jackson and we have removed the dependancy on Apache Commons Lang.

Also, this version currently doesn't support Logback Markers and Access. This
functionality is not needed at this moment in OSGi and couldn't be fixed easily.

## Usage

Two output appenders are provided:
* Syslog UDP Socket ([`LogstashSocketAppender`](/src/main/java/net/logstash/logback/appender/LogstashSocketAppender.java))
* TCP Socket ([`LogstashTcpSocketAppender`](/src/main/java/net/logstash/logback/appender/LogstashTcpSocketAppender.java))

Other logback appenders (such as `RollingFileAppender`) can use the
[`LogstashEncoder`](/src/main/java/net/logstash/logback/encoder/LogstashEncoder.java) or
[`LogstashLayout`](/src/main/java/net/logstash/logback/layout/LogstashLayout.java)
to format log messages.

### Syslog UDP Socket Appender

To output logstash compatible JSON to a syslog channel, use the `LogstashSocketAppender` in your `logback.xml` like this:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <appender name="stash" class="net.logstash.logback.appender.LogstashSocketAppender">
    <syslogHost>MyAwsomeSyslogServer</syslogHost>
  </appender>
  <root level="all">
    <appender-ref ref="stash" />
  </root>
</configuration>
```

Then use the `syslog` input in logstash like this:

```
input {
  syslog {
    type => "your-log-type"
    codec => "json"
  }
}
```

You can also use the `udp` input, which provides threading.
 
### TCP Socket Appender

Use the `LogstashEncoder` along with the `LogstashTcpSocketAppender` to log over TCP.
See the next section for an example of how to use the encoder with a file appender.

Example appender configuration in `logback.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <appender name="stash" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
      <!-- remoteHost and port are optional (default values shown) -->
      <remoteHost>127.0.0.1</remoteHost>
      <port>4560</port>
  
      <!-- encoder is required -->
      <encoder class="net.logstash.logback.encoder.LogstashEncoder" />
  </appender>
  
  <root level="DEBUG">
      <appender-ref ref="stash" />
  </root>
</configuration>
```

Example logstash configuration to read `LogstashTcpSocketAppender` messages:

```
input {
    tcp {
        port => 4560
        codec => json
    }
}
```

### Encoder / Layout

You can use the [`LogstashEncoder`](/src/main/java/net/logstash/logback/encoder/LogstashEncoder.java) or
[`LogstashLayout`](/src/main/java/net/logstash/logback/layout/LogstashLayout.java) with other logback appenders.

For example, to output logstash compatible JSON to a file, use the `LogstashEncoder` with the `RollingFileAppender` in your `logback.xml` like this:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <appender name="stash" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
      <level>info</level>
    </filter>
    <file>/some/path/to/your/file.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
      <fileNamePattern>/some/path/to/your/file.log.%d{yyyy-MM-dd}</fileNamePattern>
      <maxHistory>30</maxHistory>
    </rollingPolicy>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder" />
  </appender>
  <root level="all">
    <appender-ref ref="stash" />
  </root>
</configuration>
```

Then use the `file` input in logstash like this:

```
input {
  file {
    type => "your-log-type"
    path => "/some/path/to/your/file.log"
    codec => "json"
  }
}
```


## Fields

The fields included in the logstash event are described in the sections below.

### Standard Fields

These fields will appear in every log event unless otherwise noted.
The field names listed here are the default field names.
The field names can be customized (see below).

| Field         | Description
|---------------|------------
| `@timestamp`  | Time of the log event. (`yyyy-MM-dd'T'HH:mm:ss.SSSZZ`)
| `@version`    | Logstash format version (e.g. 1)
| `message`     | Formatted log message of the event 
| `logger_name` | Name of the logger that logged the event
| `thread_name` | Name of the thread that logged the event
| `level`       | String name of the level of the event
| `level_value` | Integer value of the level of the event
| `stack_trace` | (Only if a throwable was logged) The stacktrace of the throwable.  Stackframes are separated by line endings.

#### Caller Info Fields
The encoder/layout/appender do not contain caller info by default. 
This can be costly to calculate and should be switched off for busy production environments.

To switch it on, add the `includeCallerInfo` property to the configuration.
```xml
<encoder class="net.logstash.logback.encoder.LogstashEncoder">
  <includeCallerInfo>true</includeCallerInfo>
</encoder>
```

OR

```xml
<appender name="stash" class="net.logstash.logback.appender.LogstashSocketAppender">
  <includeCallerInfo>true</includeCallerInfo>
</appender>
```

When switched on, the following fields will be included in the log event:

| Field                | Description
|----------------------|------------
| `caller_class_name`  | Fully qualified class name of the class that logged the event
| `caller_method_name` | Name of the method that logged the event
| `caller_file_name`   | Name of the file that logged the event
| `caller_line_number` | Line number of the file where the event was logged

### Customizing Standard Field Names

The standard field names above can be customized by using the `fieldNames`
configuration element in the encoder or appender configuration

A shortened version of the field names can be configured like this:
```xml
<encoder class="net.logstash.logback.encoder.LogstashEncoder">
  <fieldNames class="net.logstash.logback.fieldnames.ShortenedFieldNames"/>
</encoder>
```
See [`ShortenedFieldNames`](/src/main/java/net/logstash/logback/fieldnames/ShortenedFieldNames.java)
for complete list of shortened names. 

If you want to customize individual field names, you can do so like this:
```xml
<encoder class="net.logstash.logback.encoder.LogstashEncoder">
  <fieldNames>
    <logger>logger</logger>
    <thread>thread</logger>
    ...
  </fieldNames>
</encoder>
```

See [`LogstashFieldNames`](/src/main/java/net/logstash/logback/fieldnames/LogstashFieldNames.java)
for all the field names that can be customized.

Prevent a field from being output by setting the field name to `[ignore]`.

Log the caller info, MDC properties, and context properties
in sub-objects within the JSON event by specifying field
names for `caller`, `mdc`, and `context`, respectively.
 
### Customizing Logger Name Field Length

You can shorten the logger name field length similar to the normal pattern style of "%logger{36}".  Examples of how it is shortened
can be found here: http://logback.qos.ch/manual/layouts.html#conversionWord

```xml
<encoder class="net.logstash.logback.encoder.LogstashEncoder">
  <shortenedLoggerNameLength>36</shortenedLoggerNameLength>
</encoder>
```

### Custom Fields

In addition to the fields above, you can add other fields to the log event either globally, or on an event-by-event basis.

#### Global Custom Fields

Add custom fields that will appear in every json event like this : 
```xml
<encoder class="net.logstash.logback.encoder.LogstashEncoder">
  <customFields>{"appname":"damnGoodWebservice","roles":["customerorder","auth"],"buildinfo":{"version":"Version 0.1.0-SNAPSHOT","lastcommit":"75473700d5befa953c45f630c6d9105413c16fe1"}}</customFields>
</encoder>
```

OR

```xml
<appender name="stash" class="net.logstash.logback.appender.LogstashSocketAppender">
  <customFields>{"appname":"damnGoodWebservice","roles":["customerorder","auth"],"buildinfo":{"version":"Version 0.1.0-SNAPSHOT","lastcommit":"75473700d5befa953c45f630c6d9105413c16fe1"}}</customFields>
</appender>
```