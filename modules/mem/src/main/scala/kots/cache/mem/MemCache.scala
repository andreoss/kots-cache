package kots.cache.mem

import cats.Functor
import cats.effect.kernel.Ref
import cats.syntax.all._
import kots.cache.Cache

object MemCache {

  /** Builds an in-memory cache over an atomically updated immutable map. */
  def of[F[_]: Functor: Ref.Make, K, V]: F[Cache[F, K, V]] =
    Ref.of[F, Map[K, V]](Map.empty).map { ref =>
      new Cache[F, K, V] {
        def get(key: K): F[Option[V]] = ref.get.map(_.get(key))
        def put(key: K, value: V): F[Unit] = ref.update(_.updated(key, value))
        def modify[A](key: K)(f: Option[V] => (Option[V], A)): F[A] =
          ref.modify { m =>
            val (next, a) = f(m.get(key))
            (next.fold(m - key)(v => m.updated(key, v)), a)
          }
        def remove(key: K): F[Unit] = ref.update(_ - key)
        def clear: F[Unit] = ref.set(Map.empty)
      }
    }
}
