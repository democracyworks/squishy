# Change Log

## Changes between Squishy 1.0 and 2.0

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
