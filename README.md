# kots-cache

Provider-agnostic caching library on Cats and Cats Effect, cross-built for
Scala 2.13 and 3. One algebra in `core`; every backend is an adapter over it.

## Build and test

    sbt -batch +test

Unit and embedded-provider tiers need no services.

## e2e tier

Backend services come from the compose stack:

    docker compose up -d --wait
    ./scripts/couchbase-init.sh

The suites in `modules/redis`, `modules/hazelcast`, `modules/infinispan`
and `modules/couchbase` run against the stack:

    sbt -batch +redis/test +hazelcast/test +infinispan/test +couchbase/test

Tear down with `docker compose down`.
