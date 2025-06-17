# XL-200 Middleware

## Ignoring LIMS Responses

In environments where the middleware should continue operating even when the
Laboratory Information Management System (LIMS) is unreachable or returns an
unexpected status code, set the `ignoreLimsResponse` flag. When enabled, the
middleware will log warnings for failed HTTP responses but will treat them as
successful.

Enable the flag with either a JVM system property:

```bash
java -DignoreLimsResponse=true -jar xl200.jar
```

or by setting the environment variable before starting the application:

```bash
export IGNORE_LIMS_RESPONSE=true
java -jar xl200.jar
```

## Custom Configuration Location

By default the middleware loads its settings from
`C:\CCMW\SysmaxXS500i\settings\XL200\config.json`. To use a different file,
provide the path via the `xl200.config.path` system property or the
`XL200_CONFIG_PATH` environment variable.

Example using the system property:

```bash
java -Dxl200.config.path=/path/to/config.json -jar xl200.jar
```

Or set the environment variable:

```bash
export XL200_CONFIG_PATH=/path/to/config.json
java -jar xl200.jar
```

## Query Record Handling

When the analyzer sends a query record the middleware now forwards the sample ID to the LIMS using the `/test_orders_for_sample_requests` endpoint. This allows the LIMS to provide any pending test orders for that sample.
