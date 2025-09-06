package kots.cache

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all._
import munit.CatsEffectSuite

final class CacheSuite extends CatsEffectSuite {

  private def cache: IO[Cache[IO, String, Int]] =
    Ref.of[IO, Map[String, Int]](Map.empty).map { ref =>
      new Cache[IO, String, Int] {
        def get(key: String): IO[Option[Int]] = ref.get.map(_.get(key))
        def put(key: String, value: Int): IO[Unit] =
          ref.update(_.updated(key, value))
        def remove(key: String): IO[Unit] = ref.update(_ - key)
        def clear: IO[Unit] = ref.set(Map.empty)
      }
    }

  test("get returns the value put under the key") {
    cache.flatMap(c => c.put("a", 1) *> c.get("a")).assertEquals(Some(1))
  }

  test("get of an absent key is empty") {
    cache.flatMap(_.get("a")).assertEquals(None)
  }

  test("put overwrites the previous value") {
    cache.flatMap(c => c.put("a", 1) *> c.put("a", 2) *> c.get("a")).assertEquals(Some(2))
  }

  test("remove deletes the key") {
    cache.flatMap(c => c.put("a", 1) *> c.remove("a") *> c.get("a")).assertEquals(None)
  }

  test("remove of an absent key is a no-op") {
    cache.flatMap(c => c.remove("a") *> c.get("a")).assertEquals(None)
  }

  test("clear empties the cache") {
    cache
      .flatMap(c => c.put("a", 1) *> c.put("b", 2) *> c.clear *> (c.get("a"), c.get("b")).tupled)
      .assertEquals((None, None))
  }
}
