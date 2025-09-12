package kots.cache

import cats.Monad
import cats.syntax.all._

object Metered {

  /** Wraps a cache so gets report hits and misses. */
  def cache[F[_]: Monad, K, V](
    underlying: Cache[F, K, V],
    metrics: CacheMetrics[F],
  ): Cache[F, K, V] =
    new Cache[F, K, V] {
      def get(key: K): F[Option[V]] =
        underlying.get(key).flatTap(r => if (r.isDefined) metrics.hit else metrics.miss)
      def put(key: K, value: V): F[Unit] = underlying.put(key, value)
      def modify[A](key: K)(f: Option[V] => (Option[V], A)): F[A] = underlying.modify(key)(f)
      def remove(key: K): F[Unit] = underlying.remove(key)
      def clear: F[Unit] = underlying.clear
    }

  /** Wraps a loading cache so real loader runs are also counted. */
  def loading[F[_]: Monad, K, V](
    underlying: LoadingCache[F, K, V],
    metrics: CacheMetrics[F],
  ): LoadingCache[F, K, V] =
    new LoadingCache[F, K, V] {
      private val metered = cache(underlying, metrics)
      def getOrLoad(key: K)(load: F[V]): F[V] =
        underlying.getOrLoad(key)(metrics.load *> load)
      def get(key: K): F[Option[V]] = metered.get(key)
      def put(key: K, value: V): F[Unit] = metered.put(key, value)
      def modify[A](key: K)(f: Option[V] => (Option[V], A)): F[A] = metered.modify(key)(f)
      def remove(key: K): F[Unit] = metered.remove(key)
      def clear: F[Unit] = metered.clear
    }
}
