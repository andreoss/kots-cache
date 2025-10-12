package kots.cache.redis

import cats.MonadThrow
import cats.syntax.all._
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effects.ScriptOutputType
import kots.cache.{Cache, Codec, CodecError}

import scala.concurrent.duration.FiniteDuration

/** Raised when a stored text form no longer parses as the typed value. */
final case class CodecException(error: CodecError)
  extends RuntimeException(error.description)

object RedisCache {

  private val casScript =
    """local cur = redis.call('GET', KEYS[1])
      |local hasExpected = ARGV[1] == '1'
      |local expected = ARGV[2]
      |local hasNext = ARGV[3] == '1'
      |local next = ARGV[4]
      |local ttlMs = tonumber(ARGV[5])
      |if (hasExpected and cur == expected) or ((not hasExpected) and cur == false) then
      |  if hasNext then
      |    if ttlMs > 0 then
      |      redis.call('SET', KEYS[1], next, 'PX', ttlMs)
      |    else
      |      redis.call('SET', KEYS[1], next)
      |    end
      |  else
      |    redis.call('DEL', KEYS[1])
      |  end
      |  return 1
      |else
      |  return 0
      |end""".stripMargin

  /** Wraps Redis string commands behind the cache algebra. */
  def of[F[_], K, V](
    commands: RedisCommands[F, String, String],
    keyCodec: Codec[K],
    valueCodec: Codec[V],
    namespace: String,
    timeToLive: Option[FiniteDuration],
  )(implicit F: MonadThrow[F]): Cache[F, K, V] =
    new Cache[F, K, V] {
      private def raw(key: K): String = s"$namespace:${keyCodec.encode(key)}"

      private def parse(text: String): F[V] =
        valueCodec.decode(text).leftMap(CodecException.apply).liftTo[F]

      def get(key: K): F[Option[V]] =
        commands.get(raw(key)).flatMap(_.traverse(parse))

      def put(key: K, value: V): F[Unit] =
        timeToLive match {
          case Some(ttl) => commands.setEx(raw(key), valueCodec.encode(value), ttl)
          case None      => commands.set(raw(key), valueCodec.encode(value))
        }

      def modify[A](key: K)(f: Option[V] => (Option[V], A)): F[A] =
        attemptModify(key)(f).untilDefinedM

      private def attemptModify[A](key: K)(f: Option[V] => (Option[V], A)): F[Option[A]] =
        for {
          current <- commands.get(raw(key))
          typed <- current.traverse(parse)
          (next, a) = f(typed)
          ttlMs = timeToLive.fold(0L)(_.toMillis).toString
          args = List(
            current.fold("0")(_ => "1"),
            current.getOrElse(""),
            next.fold("0")(_ => "1"),
            next.fold("")(valueCodec.encode),
            ttlMs,
          )
          won <- commands.eval(casScript, ScriptOutputType.Integer, List(raw(key)), args)
        } yield Option.when(won == 1L)(a)

      def remove(key: K): F[Unit] = commands.del(raw(key)).void

      def clear: F[Unit] =
        commands.keys(s"$namespace:*").flatMap {
          case Nil          => F.unit
          case head :: tail => commands.del(head, tail: _*).void
        }
    }
}
