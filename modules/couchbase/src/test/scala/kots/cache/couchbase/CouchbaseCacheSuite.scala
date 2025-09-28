package kots.cache.couchbase

import cats.effect.IO
import com.couchbase.client.java.Cluster
import com.couchbase.client.java.manager.query.CreatePrimaryQueryIndexOptions
import kots.cache.{Cache, CacheContract, Codec}

final class CouchbaseCacheSuite extends CacheContract {

  private lazy val cluster = Cluster.connect("couchbase://127.0.0.1", "Administrator", "password")

  private lazy val collection = {
    val bucket = cluster.bucket("cache")
    bucket.waitUntilReady(java.time.Duration.ofSeconds(60))
    cluster
      .queryIndexes()
      .createPrimaryIndex(
        "cache",
        CreatePrimaryQueryIndexOptions.createPrimaryQueryIndexOptions().ignoreIfExists(true),
      )
    bucket.defaultCollection()
  }

  override def afterAll(): Unit = {
    cluster.disconnect()
    super.afterAll()
  }

  def cache: IO[Cache[IO, String, Int]] =
    IO {
      CouchbaseCache.of[IO, String, Int](
        cluster,
        collection,
        bucketName = "cache",
        keyCodec = Codec.string,
        valueCodec = Codec.int,
        namespace = s"contract-${java.util.UUID.randomUUID()}",
        timeToLive = None,
      )
    }
}
