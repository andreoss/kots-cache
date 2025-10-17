package kots.cache

import cats.{Monad, MonadThrow}
import cats.effect.kernel.Clock
import cats.syntax.all._

object Metered {

  /** Wraps a cache so gets report hits, misses and latency. */
  def cache[F[_]: Monad: Clock, K, V](
    underlying: Cache[F, K, V],
    metrics: CacheMetrics[F],
  ): Cache[F, K, V] =
    new Cache[F, K, V] {
      def get(key: K): F[Option[V]] =
        Clock[F].timed(underlying.get(key)).flatMap { case (elapsed, result) =>
          result.fold(metrics.miss)(_ => metrics.hit) *>
            metrics.getLatency(elapsed).as(result)
        }
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
      def getOrLoad(key: K)(load: F[V]): F[V] =
        underlying.getOrLoad(key) {
          metrics.load *> Clock[F].timed(load.attempt).flatMap { case (elapsed, outcome) =>
            metrics.loadLatency(elapsed, outcome.isRight) *> outcome.liftTo[F]
          }
        }
      def get(key: K): F[Option[V]] = metered.get(key)
      def put(key: K, value: V): F[Unit] = metered.put(key, value)
      def modify[A](key: K)(f: Option[V] => (Option[V], A)): F[A] = metered.modify(key)(f)
      def remove(key: K): F[Unit] = metered.remove(key)
      def clear: F[Unit] = metered.clear
    }
}
