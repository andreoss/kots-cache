package kots.cache.couchbase

import cats.effect.IO
import cats.syntax.all._
import com.couchbase.client.java.Cluster
import com.couchbase.client.java.kv.GetOptions
import kots.cache.Codec
import munit.CatsEffectSuite

import scala.concurrent.duration._

final class CouchbaseTtlSuite extends CatsEffectSuite {

  private lazy val cluster = Cluster.connect("couchbase://127.0.0.1", "Administrator", "password")

  private lazy val collection = {
    val bucket = cluster.bucket("cache")
    bucket.waitUntilReady(java.time.Duration.ofSeconds(60))
    bucket.defaultCollection()
  }

  override def afterAll(): Unit = {
    cluster.disconnect()
    super.afterAll()
  }

  private def cacheIn(ns: String, ttl: FiniteDuration) =
    CouchbaseCache.of[IO, String, Int](
      cluster,
      collection,
      bucketName = "cache",
      keyCodec = Codec.string,
      valueCodec = Codec.int,
      namespace = ns,
      timeToLive = Some(ttl),
    )

  test("the time-to-live lands as backend expiry") {
    val ns = s"ttl-${java.util.UUID.randomUUID()}"
    val c = cacheIn(ns, 30.seconds)
    c.put("a", 1) *> IO.blocking {
      val expiry =
        collection.get(s"$ns:a", GetOptions.getOptions().withExpiry(true)).expiryTime()
      assert(expiry.isPresent)
    }
  }

  test("an entry expires on the backend once its time-to-live elapses") {
    val ns = s"exp-${java.util.UUID.randomUUID()}"
    val c = cacheIn(ns, 1.second)
    c.put("a", 1) *> IO.sleep(2500.millis) *> c.get("a").assertEquals(None)
  }
}
