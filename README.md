# kots-cache

Provider-agnostic caching library on Cats and Cats Effect, cross-built for
Scala 2.13 and 3. One algebra in `core`; every backend is an adapter over it.
The core depends on Cats and Cats Effect only.

## Algebra

`Cache[F, K, V]` is `get`, `put`, `modify`, `remove`, `clear`. `core` adds
expiry (time-to-live, time-to-idle), single-flight loading, size bounds with
LRU eviction, a text `Codec` port and a `CacheMetrics` port.

```scala
import cats.effect.IO
import kots.cache.{Expiry, LoadingCache}
import kots.cache.mem.MemCache
import scala.concurrent.duration._

for {
  base  <- MemCache.expiring[IO, String, Int](Expiry.ttl(5.minutes))
  cache <- LoadingCache.singleFlight(base)
  value <- cache.getOrLoad("k")(IO.pure(42))
} yield value
```

`Metered.cache` and `Metered.loading` wrap any cache with the metrics port;
`CacheInterop.mapK` views one through another effect.

## Providers

| Provider | Module | Adapter | Tier |
| --- | --- | --- | --- |
| In-memory (`Ref`) | `mem` | expiry, LRU bound, metrics | unit |
| Caffeine | `caffeine` | native cache | unit |
| Caffeine, Ehcache 3, Infinispan, Cache2k | `jcache` | JSR-107 entry processors | embedded |
| Redis | `redis` | redis4cats, Lua CAS, backend TTL | e2e |
| Hazelcast | `hazelcast` | client `IMap` CAS | e2e |
| Infinispan server | `infinispan` | Hot Rod versioned CAS, entry lifespan | e2e |
| Couchbase | `couchbase` | Java SDK document CAS, backend TTL | e2e |

`prometheus` binds the metrics port to the Prometheus Java client, `interop`
carries the algebra into another effect system, `bench` times `mem` against a
bare map. Every adapter passes the same contract suite.

## Build and test

    sbt -batch +test

Unit and embedded-provider tiers need no services.

## e2e tier

Backend services come from the compose stack:

    docker compose up -d --wait
    ./scripts/couchbase-init.sh

    sbt -batch +redis/test +hazelcast/test +infinispan/test +couchbase/test

The metrics e2e suite in `modules/prometheus` exposes a workload on
`127.0.0.1:19095` and asserts the composed Prometheus (host network,
`scripts/prometheus.yml`) scrapes it:

    sbt -batch +prometheus/test

Tear down with `docker compose down`.
