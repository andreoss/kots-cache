package kots.cache.hazelcast

import cats.effect.kernel.Sync
import cats.syntax.all._
import com.hazelcast.map.IMap
import kots.cache.{Cache, Retry}

object HazelcastCache {

  /** Wraps a Hazelcast map; modify retries over the client CAS primitives. */
  def of[F[_], K, V](map: IMap[K, V], retry: Retry = Retry.default)(
    implicit F: Sync[F]
  ): Cache[F, K, V] =
    new Cache[F, K, V] {
      def get(key: K): F[Option[V]] = F.blocking(Option(map.get(key)))

      def put(key: K, value: V): F[Unit] = F.blocking(map.set(key, value))

      def modify[A](key: K)(f: Option[V] => (Option[V], A)): F[A] =
        retry.cas(attempt(key)(f))

      private def attempt[A](key: K)(f: Option[V] => (Option[V], A)): F[Option[A]] =
        F.blocking {
          val current = Option(map.get(key))
          val (next, a) = f(current)
          val won = (current, next) match {
            case (Some(c), Some(n)) => map.replace(key, c, n)
            case (Some(c), None)    => map.remove(key, c)
            case (None, Some(n))    => map.putIfAbsent(key, n) == null
            case (None, None)       => true
          }
          Option.when(won)(a)
        }

      def remove(key: K): F[Unit] = F.blocking(map.delete(key))

      def clear: F[Unit] = F.blocking(map.clear())
    }
}
