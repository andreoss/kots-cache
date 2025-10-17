package kots.cache

import cats.data.OptionT
import cats.effect.kernel.{Concurrent, Deferred, Ref}
import cats.effect.syntax.all._
import cats.syntax.all._

import java.util.concurrent.CancellationException

/** Cache that loads absent values, collapsing concurrent loads per key. */
trait LoadingCache[F[_], K, V] extends Cache[F, K, V] {
  def getOrLoad(key: K)(load: F[V]): F[V]
}

object LoadingCache {

  private type Flight[F[_], V] = Deferred[F, Either[Throwable, V]]

  /** Wraps a cache so concurrent loads of one key run the loader once. */
  def singleFlight[F[_], K, V](
    underlying: Cache[F, K, V],
  )(implicit F: Concurrent[F]): F[LoadingCache[F, K, V]] =
    F.ref(Map.empty[K, Flight[F, V]]).map { flights =>
      new LoadingCache[F, K, V] {
        def getOrLoad(key: K)(load: F[V]): F[V] =
          OptionT(underlying.get(key)).getOrElseF {
            F.deferred[Either[Throwable, V]].flatMap { d =>
              flights.modify { m =>
                m.get(key) match {
                  case Some(inFlight) => (m, inFlight.get.rethrow)
                  case None           => (m.updated(key, d), lead(key, load, d))
                }
              }.flatten
            }
          }

        private def lead(key: K, load: F[V], d: Flight[F, V]): F[V] =
          load.attempt
            .flatTap(_.traverse_(underlying.put(key, _)))
            .productL(flights.update(_ - key))
            .flatTap(d.complete)
            .rethrow
            .onCancel(
              flights.update(_ - key) *>
                d.complete(Left(new CancellationException("load canceled"))).void,
            )

        def get(key: K): F[Option[V]] = underlying.get(key)
        def put(key: K, value: V): F[Unit] = underlying.put(key, value)
        def modify[A](key: K)(f: Option[V] => (Option[V], A)): F[A] = underlying.modify(key)(f)
        def remove(key: K): F[Unit] = underlying.remove(key)
        def clear: F[Unit] = underlying.clear
      }
    }
}
