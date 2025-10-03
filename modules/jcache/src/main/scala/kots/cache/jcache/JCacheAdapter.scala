package kots.cache.jcache

import cats.effect.kernel.Sync
import cats.syntax.all._
import javax.cache.{Cache => JCache}
import javax.cache.processor.{EntryProcessor, MutableEntry}
import kots.cache.Cache

object JCacheAdapter {

  /** Wraps a JSR-107 cache; every backend call is suspended in Sync. */
  def of[F[_], K, V](underlying: JCache[K, V])(implicit F: Sync[F]): Cache[F, K, V] =
    new Cache[F, K, V] {
      def get(key: K): F[Option[V]] = F.delay(Option(underlying.get(key)))

      def put(key: K, value: V): F[Unit] = F.delay(underlying.put(key, value))

      def modify[A](key: K)(f: Option[V] => (Option[V], A)): F[A] =
        F.delay {
          underlying.invoke(
            key,
            new EntryProcessor[K, V, A] {
              def process(entry: MutableEntry[K, V], args: AnyRef*): A = {
                val current = if (entry.exists) Some(entry.getValue) else None
                val (next, a) = f(current)
                next match {
                  case Some(v) => entry.setValue(v)
                  case None    => if (entry.exists) entry.remove()
                }
                a
              }
            },
          )
        }

      def remove(key: K): F[Unit] = F.delay(underlying.remove(key)).void

      def clear: F[Unit] = F.delay(underlying.clear())
    }
}
