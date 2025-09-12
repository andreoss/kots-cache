package kots.cache

import cats.Applicative

/** Counters a cache reports to; carries no key or value contents. */
trait CacheMetrics[F[_]] {
  def hit: F[Unit]
  def miss: F[Unit]
  def load: F[Unit]
  def eviction: F[Unit]
}

object CacheMetrics {

  /** Metrics sink that discards every signal. */
  def noop[F[_]](implicit F: Applicative[F]): CacheMetrics[F] =
    new CacheMetrics[F] {
      def hit: F[Unit] = F.unit
      def miss: F[Unit] = F.unit
      def load: F[Unit] = F.unit
      def eviction: F[Unit] = F.unit
    }
}
