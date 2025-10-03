package kots.cache.prometheus

import cats.effect.kernel.Sync
import io.prometheus.metrics.core.metrics.{Counter, Histogram}
import io.prometheus.metrics.model.registry.PrometheusRegistry
import kots.cache.CacheMetrics

import scala.concurrent.duration.FiniteDuration

/** Cache metric families on one registry; bind a cache with forCache. */
final class PrometheusCacheMetrics private (
  gets: Counter,
  loads: Counter,
  evictions: Counter,
  getLatencies: Histogram,
  loadLatencies: Histogram,
  entryLifetimes: Histogram,
) {

  /** Metrics sink for one named cache; the name is the only label. */
  def forCache[F[_]](name: String)(implicit F: Sync[F]): CacheMetrics[F] =
    new CacheMetrics[F] {
      private val hits = gets.labelValues(name, "hit")
      private val misses = gets.labelValues(name, "miss")
      private val loaded = loads.labelValues(name)
      private val evicted = evictions.labelValues(name)
      private val getObserved = getLatencies.labelValues(name)
      private val loadSucceeded = loadLatencies.labelValues(name, "success")
      private val loadFailed = loadLatencies.labelValues(name, "failure")
      private val lifetimes = entryLifetimes.labelValues(name)

      private def seconds(duration: FiniteDuration): Double =
        duration.toNanos.toDouble / 1e9

      def hit: F[Unit] = F.delay(hits.inc())
      def miss: F[Unit] = F.delay(misses.inc())
      def load: F[Unit] = F.delay(loaded.inc())
      def eviction: F[Unit] = F.delay(evicted.inc())
      def getLatency(duration: FiniteDuration): F[Unit] =
        F.delay(getObserved.observe(seconds(duration)))
      def loadLatency(duration: FiniteDuration, success: Boolean): F[Unit] =
        F.delay((if (success) loadSucceeded else loadFailed).observe(seconds(duration)))
      def entryLifetime(age: FiniteDuration): F[Unit] =
        F.delay(lifetimes.observe(seconds(age)))
    }
}

object PrometheusCacheMetrics {

  /** Registers the cache metric families once on the registry. */
  def register(registry: PrometheusRegistry): PrometheusCacheMetrics =
    new PrometheusCacheMetrics(
      Counter.builder().name("cache_gets").help("Cache gets by result.")
        .labelNames("cache", "result").register(registry),
      Counter.builder().name("cache_loads").help("Cache loader runs.")
        .labelNames("cache").register(registry),
      Counter.builder().name("cache_evictions").help("Cache evictions.")
        .labelNames("cache").register(registry),
      Histogram.builder().name("cache_get_latency_seconds").help("Get latency.")
        .labelNames("cache").register(registry),
      Histogram.builder().name("cache_load_latency_seconds").help("Loader latency by outcome.")
        .labelNames("cache", "outcome").register(registry),
      Histogram.builder().name("cache_entry_lifetime_seconds").help("Entry age at expiry death.")
        .labelNames("cache").register(registry),
    )
}
