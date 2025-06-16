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
