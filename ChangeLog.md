# Change Log

## Changes between 3.0.1 and 3.1.0

### Improved Region configuration support

Whereas before the only acceptable string format for the `#aws/region` tag
was the region enum format (i.e. "US_WEST_1"), the tag can now also take the
more common name format (i.e. "us-west-1"). The `start-consumer` now can take
either of the string formats or a `com.amazonaws.regions.Region` instance, whereas
before it only took the instance type.

See the Region section of the README.

## Changes between 3.0.0 and 3.0.1

### Excluded `logback.xml` from library JAR file

Logging config files don't belong in library JARs, and having this one in there
resulted in logback-using consumer code generating warnings about multiple
logback configs on the classpath. Library consumers should get full control
over their logging configs, warning-free.

## Changes between 2.0.0 and 3.0.0

### `consume-messages` now returns a consumer-id instead of a future

In 1.0.0 it returned a future that you could cancel when you wanted to stop
consuming messages. But in 2.0.0 that stopped working because of the retry
loop added in that version.

In 3.0.0 it will return a consumer-id instead. You can then call
`(squishy.core/stop-consumer consumer-id)` when you want to stop processing.

**This is a breaking change.**

### `consume-messages` will now handle increasing the visibility timeout of SQS messages

By default SQS queues have a visibility timeout of 30 seconds (though you can
increase it in each queue's config). This means that if it takes your code
longer than that timeout to process a message, SQS will make it available to
other consumers. In 3.0.0, squishy will now tell SQS to increase the visibility
timeout of a message that is still being processed so that you don't have
redundant processing.

## Changes between Squishy 1.0.0 and 2.0.0

### `consume-messages` can recover from some SQS connection problems

`consume-messages` used to not be able to recover from, for example, a
connection reset when trying to read a message from the queue. And
since `consume-messages` does its work in a separate thread, the
exception thrown would not be handled. Now, however, Squishy will
attempt to retry, backing off, before finally giving up, logging a
fatal error, and realizing the future.

This required the first argument for `consume-messages` to
change. Instead of passing a client, you provide a map of
configuration containing your `:access-key`, `:access-secret` and
`:region`. Squishy will create clients as needed.

**This is a breaking change.**
