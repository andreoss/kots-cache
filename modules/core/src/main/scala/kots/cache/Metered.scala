package kots.cache

import cats.MonadThrow
import cats.effect.kernel.Clock
import cats.syntax.all._

object Metered {

  /** Wraps a cache so gets report hits, misses and latency. */
  def cache[F[_]: MonadThrow: Clock, K, V](
    underlying: Cache[F, K, V],
    metrics: CacheMetrics[F],
  ): Cache[F, K, V] =
    new Cache[F, K, V] {
      def get(key: K): F[Option[V]] =
        for {
          start <- Clock[F].monotonic
          result <- underlying.get(key)
          end <- Clock[F].monotonic
          _ <- if (result.isDefined) metrics.hit else metrics.miss
          _ <- metrics.getLatency(end - start)
        } yield result
      def put(key: K, value: V): F[Unit] = underlying.put(key, value)
      def modify[A](key: K)(f: Option[V] => (Option[V], A)): F[A] = underlying.modify(key)(f)
      def remove(key: K): F[Unit] = underlying.remove(key)
      def clear: F[Unit] = underlying.clear
    }

  /** Wraps a loading cache so real loader runs report count, latency, outcome. */
  def loading[F[_]: MonadThrow: Clock, K, V](
    underlying: LoadingCache[F, K, V],
    metrics: CacheMetrics[F],
  ): LoadingCache[F, K, V] =
    new LoadingCache[F, K, V] {
      private val metered = cache(underlying, metrics)
      def getOrLoad(key: K)(load: F[V]): F[V] = {
        val timed = for {
          _ <- metrics.load
          start <- Clock[F].monotonic
          outcome <- load.attempt
          end <- Clock[F].monotonic
          _ <- metrics.loadLatency(end - start, outcome.isRight)
          value <- outcome.liftTo[F]
        } yield value
        underlying.getOrLoad(key)(timed)
      }
      def get(key: K): F[Option[V]] = metered.get(key)
      def put(key: K, value: V): F[Unit] = metered.put(key, value)
      def modify[A](key: K)(f: Option[V] => (Option[V], A)): F[A] = metered.modify(key)(f)
      def remove(key: K): F[Unit] = metered.remove(key)
      def clear: F[Unit] = metered.clear
    }
}
