package kots.cache.couchbase

import cats.effect.kernel.Sync
import cats.syntax.all._
import com.couchbase.client.core.error.{
  CasMismatchException,
  DocumentExistsException,
  DocumentNotFoundException,
}
import com.couchbase.client.java.{Cluster, Collection}
import com.couchbase.client.java.codec.RawStringTranscoder
import com.couchbase.client.java.json.JsonObject
import com.couchbase.client.java.kv.{
  GetOptions,
  InsertOptions,
  RemoveOptions,
  ReplaceOptions,
  UpsertOptions,
}
import com.couchbase.client.java.query.{QueryOptions, QueryScanConsistency}
import kots.cache.{Cache, Codec, CodecError, Retry}

import scala.concurrent.duration.FiniteDuration

/** Raised when a stored text form no longer parses as the typed value. */
final case class CouchbaseCodecException(error: CodecError)
  extends RuntimeException(error.description)

object CouchbaseCache {

  /** Wraps a Couchbase collection; modify retries over document CAS. */
  def of[F[_], K, V](
    cluster: Cluster,
    collection: Collection,
    bucketName: String,
    keyCodec: Codec[K],
    valueCodec: Codec[V],
    namespace: String,
    timeToLive: Option[FiniteDuration],
    retry: Retry = Retry.default,
  )(implicit F: Sync[F]): Cache[F, K, V] =
    new Cache[F, K, V] {
      private val raw = RawStringTranscoder.INSTANCE
      private val ttl = timeToLive.map(d => java.time.Duration.ofMillis(d.toMillis))

      private def id(key: K): String = s"$namespace:${keyCodec.encode(key)}"

      private def parse(text: String): F[V] =
        valueCodec.decode(text).leftMap(CouchbaseCodecException.apply).liftTo[F]

      private def expiring[O](base: O)(withExpiry: (O, java.time.Duration) => O): O =
        ttl.fold(base)(withExpiry(base, _))

      def get(key: K): F[Option[V]] =
        F.blocking {
          try
            Some(
              collection
                .get(id(key), GetOptions.getOptions().transcoder(raw))
                .contentAs(classOf[String]),
            )
          catch { case _: DocumentNotFoundException => None }
        }.flatMap(_.traverse(parse))

      def put(key: K, value: V): F[Unit] =
        F.blocking {
          val options = expiring(UpsertOptions.upsertOptions().transcoder(raw))(_.expiry(_))
          collection.upsert(id(key), valueCodec.encode(value), options)
        }.void

      def modify[A](key: K)(f: Option[V] => (Option[V], A)): F[A] =
        retry.cas(attempt(key)(f))

      private def attempt[A](key: K)(f: Option[V] => (Option[V], A)): F[Option[A]] =
        for {
          fetched <- F.blocking {
            try {
              val result = collection.get(id(key), GetOptions.getOptions().transcoder(raw))
              (Some(result.contentAs(classOf[String])), result.cas())
            } catch { case _: DocumentNotFoundException => (Option.empty[String], 0L) }
          }
          (currentText, cas) = fetched
          current <- currentText.traverse(parse)
          (next, a) = f(current)
          won <- F.blocking {
            try {
              (current, next) match {
                case (Some(_), Some(n)) =>
                  val options =
                    expiring(ReplaceOptions.replaceOptions().transcoder(raw).cas(cas))(_.expiry(_))
                  collection.replace(id(key), valueCodec.encode(n), options)
                case (Some(_), None) =>
                  collection.remove(id(key), RemoveOptions.removeOptions().cas(cas))
                case (None, Some(n)) =>
                  val options = expiring(InsertOptions.insertOptions().transcoder(raw))(_.expiry(_))
                  collection.insert(id(key), valueCodec.encode(n), options)
                case (None, None) => ()
              }
              true
            } catch {
              case _: CasMismatchException      => false
              case _: DocumentNotFoundException => false
              case _: DocumentExistsException   => false
            }
          }
        } yield Option.when(won)(a)

      def remove(key: K): F[Unit] =
        F.blocking(
          try collection.remove(id(key))
          catch { case _: DocumentNotFoundException => () },
        ).void

      def clear: F[Unit] =
        F.blocking(
          cluster.query(
            s"DELETE FROM `$bucketName` WHERE META().id LIKE $$pattern",
            QueryOptions
              .queryOptions()
              .parameters(JsonObject.create().put("pattern", s"$namespace:%"))
              .scanConsistency(QueryScanConsistency.REQUEST_PLUS),
          ),
        ).void
    }
}
