package kots.cache.interop

import cats.~>
import kots.cache.Cache

object CacheInterop {

  /** Views a cache through another effect via a natural transformation. */
  def mapK[F[_], G[_], K, V](cache: Cache[F, K, V])(fk: F ~> G): Cache[G, K, V] =
    new Cache[G, K, V] {
      def get(key: K): G[Option[V]] = fk(cache.get(key))
      def put(key: K, value: V): G[Unit] = fk(cache.put(key, value))
      def modify[A](key: K)(f: Option[V] => (Option[V], A)): G[A] = fk(cache.modify(key)(f))
      def remove(key: K): G[Unit] = fk(cache.remove(key))
      def clear: G[Unit] = fk(cache.clear)
    }
}
