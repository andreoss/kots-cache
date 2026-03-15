package kots.cache.redis

import cats.effect.IO
import cats.syntax.all._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effect.Log.NoOp._
import kots.cache.Codec
import munit.CatsEffectSuite

import java.util.UUID

final class RedisClearSuite extends CatsEffectSuite {

  private lazy val allocated: (RedisCommands[IO, String, String], IO[Unit]) =
    Redis[IO].utf8("redis://127.0.0.1").allocated.unsafeRunSync()(munitIORuntime)

  override def afterAll(): Unit = {
    allocated._2.unsafeRunSync()(munitIORuntime)
    super.afterAll()
  }

  private def cacheIn(ns: String) =
    RedisCache.of[IO, String, Int](
      allocated._1,
      Codec.string,
      Codec.int,
      namespace = ns,
      timeToLive = None,
    )

  private val Calls = "calls=(\\d+)".r

  private def commandCalls(name: String): IO[Long] =
    allocated._1.info("commandstats").map { stats =>
      stats.get(name).flatMap(_.split(',').collectFirst {
        case Calls(count) => count.toLong
      }).getOrElse(0L)
    }

  test("clear scans its namespace without listing the whole keyspace") {
    val ns = s"scan-${UUID.randomUUID()}"
    val c = cacheIn(ns)
    val keys = (1 to 300).toList.map(i => s"k$i")
    for {
      _         <- keys.traverse_(k => c.put(k, 1))
      keysBefore <- commandCalls("cmdstat_keys")
      scanBefore <- commandCalls("cmdstat_scan")
      _          <- c.clear
      keysAfter  <- commandCalls("cmdstat_keys")
      scanAfter  <- commandCalls("cmdstat_scan")
      left       <- keys.traverse(c.get)
    } yield {
      assertEquals(keysAfter - keysBefore, 0L)
      assert(scanAfter - scanBefore > 1L)
      assert(left.forall(_.isEmpty))
    }
  }

  test("clear removes every key of its namespace across scan pages") {
    val cleared = cacheIn(s"clear-${UUID.randomUUID()}")
    val kept = cacheIn(s"keep-${UUID.randomUUID()}")
    val keys = (1 to 300).toList.map(i => s"k$i")
    keys.traverse_(k => cleared.put(k, 1)) *>
      kept.put("k", 1) *>
      cleared.clear *>
      keys.traverse(cleared.get).map(vs => assert(vs.forall(_.isEmpty))) *>
      kept.get("k").assertEquals(Some(1))
  }

  test("clear of an empty namespace leaves the others alone") {
    val cleared = cacheIn(s"empty-${UUID.randomUUID()}")
    val kept = cacheIn(s"keep-${UUID.randomUUID()}")
    kept.put("k", 1) *> cleared.clear *> kept.get("k").assertEquals(Some(1))
  }
}
