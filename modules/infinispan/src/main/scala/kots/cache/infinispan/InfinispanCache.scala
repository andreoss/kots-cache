package kots.cache.infinispan

import cats.effect.kernel.Sync
import cats.syntax.all._
import kots.cache.{Cache, Codec, CodecError}
import org.infinispan.client.hotrod.{Flag, RemoteCache}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

/** Raised when a stored text form no longer parses as the typed value. */
final case class InfinispanCodecException(error: CodecError)
  extends RuntimeException(error.description)

object InfinispanCache {

  /** Wraps a Hot Rod remote cache; modify retries over versioned CAS. */
  def of[F[_], K, V](
    remote: RemoteCache[String, String],
    keyCodec: Codec[K],
    valueCodec: Codec[V],
    timeToLive: Option[FiniteDuration],
  )(implicit F: Sync[F]): Cache[F, K, V] =
    new Cache[F, K, V] {
      private val ttlMs = timeToLive.map(_.toMillis)

      private def id(key: K): String = keyCodec.encode(key)

      private def parse(text: String): F[V] =
        valueCodec.decode(text).leftMap(InfinispanCodecException.apply).liftTo[F]

      def get(key: K): F[Option[V]] =
        F.blocking(Option(remote.get(id(key)))).flatMap(_.traverse(parse))

      def put(key: K, value: V): F[Unit] =
        F.blocking {
          ttlMs match {
            case Some(ms) => remote.put(id(key), valueCodec.encode(value), ms, TimeUnit.MILLISECONDS)
            case None     => remote.put(id(key), valueCodec.encode(value))
          }
          ()
        }

      def modify[A](key: K)(f: Option[V] => (Option[V], A)): F[A] =
        attempt(key)(f).untilDefinedM

      private def attempt[A](key: K)(f: Option[V] => (Option[V], A)): F[Option[A]] =
        F.blocking(Option(remote.getWithMetadata(id(key)))).flatMap { meta =>
          meta.traverse(m => parse(m.getValue)).flatMap { current =>
            val (next, a) = f(current)
            F.blocking {
              val won = (meta, next) match {
                case (Some(m), Some(n)) =>
                  ttlMs match {
                    case Some(ms) =>
                      remote.replaceWithVersion(
                        id(key),
                        valueCodec.encode(n),
                        m.getVersion,
                        ms,
                        TimeUnit.MILLISECONDS,
                        -1,
                        TimeUnit.MILLISECONDS,
                      )
                    case None =>
                      remote.replaceWithVersion(id(key), valueCodec.encode(n), m.getVersion)
                  }
                case (Some(m), None) => remote.removeWithVersion(id(key), m.getVersion)
                case (None, Some(n)) =>
                  val returning = remote.withFlags(Flag.FORCE_RETURN_VALUE)
                  ttlMs match {
                    case Some(ms) =>
                      returning.putIfAbsent(id(key), valueCodec.encode(n), ms, TimeUnit.MILLISECONDS) == null
                    case None => returning.putIfAbsent(id(key), valueCodec.encode(n)) == null
                  }
                case (None, None) => true
              }
              Option.when(won)(a)
            }
          }
        }

      def remove(key: K): F[Unit] = F.blocking(remote.remove(id(key))).void

      def clear: F[Unit] = F.blocking(remote.clear())
    }
}
