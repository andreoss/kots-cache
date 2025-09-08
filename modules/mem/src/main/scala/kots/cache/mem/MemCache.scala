package kots.cache.mem

import cats.{Functor, Monad}
import cats.effect.kernel.{Clock, Ref}
import cats.syntax.all._
import kots.cache.{Cache, Expiry}

import scala.concurrent.duration.FiniteDuration

object MemCache {

  private final case class Entry[V](value: V, writeAt: FiniteDuration, touchAt: FiniteDuration)

  private final case class Stamped[V](value: V, stamp: Long)
  private final case class Recency[K, V](entries: Map[K, Stamped[V]], tick: Long)

  /** Builds an in-memory cache evicting the least recently used entry. */
  def bounded[F[_]: Functor: Ref.Make, K, V](maximum: Int): F[Cache[F, K, V]] =
    Ref.of[F, Recency[K, V]](Recency(Map.empty, 0L)).map { ref =>
      def within(entries: Map[K, Stamped[V]]): Map[K, Stamped[V]] =
        if (entries.size <= maximum) entries
        else entries - entries.minBy(_._2.stamp)._1

      new Cache[F, K, V] {
        def get(key: K): F[Option[V]] =
          ref.modify { s =>
            s.entries.get(key) match {
              case Some(e) =>
                (Recency(s.entries.updated(key, e.copy(stamp = s.tick)), s.tick + 1), Some(e.value))
              case None => (s, None)
            }
          }

        def put(key: K, value: V): F[Unit] =
          ref.update { s =>
            Recency(within(s.entries.updated(key, Stamped(value, s.tick))), s.tick + 1)
          }

        def modify[A](key: K)(f: Option[V] => (Option[V], A)): F[A] =
          ref.modify { s =>
            val (next, a) = f(s.entries.get(key).map(_.value))
            val entries = next match {
              case Some(v) => within(s.entries.updated(key, Stamped(v, s.tick)))
              case None    => s.entries - key
            }
            (Recency(entries, s.tick + 1), a)
          }

        def remove(key: K): F[Unit] =
          ref.update(s => s.copy(entries = s.entries - key))

        def clear: F[Unit] = ref.update(_.copy(entries = Map.empty))
      }
    }

  /** Builds an in-memory cache whose entries follow the expiry policy. */
  def expiring[F[_]: Monad: Clock: Ref.Make, K, V](expiry: Expiry): F[Cache[F, K, V]] =
    Ref.of[F, Map[K, Entry[V]]](Map.empty).map { ref =>
      def dead(e: Entry[V], now: FiniteDuration): Boolean =
        expiry.timeToLive.exists(ttl => now - e.writeAt >= ttl) ||
          expiry.timeToIdle.exists(tti => now - e.touchAt >= tti)

      new Cache[F, K, V] {
        def get(key: K): F[Option[V]] =
          Clock[F].monotonic.flatMap { now =>
            ref.modify { m =>
              m.get(key) match {
                case Some(e) if dead(e, now) => (m - key, None)
                case Some(e)                 => (m.updated(key, e.copy(touchAt = now)), Some(e.value))
                case None                    => (m, None)
              }
            }
          }

        def put(key: K, value: V): F[Unit] =
          Clock[F].monotonic.flatMap(now => ref.update(_.updated(key, Entry(value, now, now))))

        def modify[A](key: K)(f: Option[V] => (Option[V], A)): F[A] =
          Clock[F].monotonic.flatMap { now =>
            ref.modify { m =>
              val live = m.get(key).filterNot(dead(_, now))
              val (next, a) = f(live.map(_.value))
              (next.fold(m - key)(v => m.updated(key, Entry(v, now, now))), a)
            }
          }

        def remove(key: K): F[Unit] = ref.update(_ - key)
        def clear: F[Unit] = ref.set(Map.empty)
      }
    }

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
