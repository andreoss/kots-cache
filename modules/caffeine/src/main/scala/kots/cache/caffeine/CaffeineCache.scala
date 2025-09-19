package kots.cache.caffeine

import cats.effect.kernel.Sync
import com.github.benmanes.caffeine.cache.{Cache => CCache}
import kots.cache.Cache

object CaffeineCache {

  /** Wraps a caffeine cache; every backend call is suspended in Sync. */
  def of[F[_], K, V](underlying: CCache[K, V])(implicit F: Sync[F]): Cache[F, K, V] =
    new Cache[F, K, V] {
      def get(key: K): F[Option[V]] = F.delay(Option(underlying.getIfPresent(key)))

      def put(key: K, value: V): F[Unit] = F.delay(underlying.put(key, value))

      def modify[A](key: K)(f: Option[V] => (Option[V], A)): F[A] =
        F.delay {
          var out: Option[A] = None
          underlying
            .asMap()
            .compute(
              key,
              (_, current) => {
                val (next, a) = f(Option(current))
                out = Some(a)
                next.getOrElse(null.asInstanceOf[V])
              },
            )
          out.get
        }

      def remove(key: K): F[Unit] = F.delay(underlying.invalidate(key))

      def clear: F[Unit] = F.delay(underlying.invalidateAll())
    }
}
