package kots.cache

import cats.effect.IO
import cats.syntax.all._
import munit.CatsEffectSuite

/** Contract every Cache adapter must satisfy; bind `cache` to run it. */
abstract class CacheContract extends CatsEffectSuite {

  def cache: IO[Cache[IO, String, Int]]

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

  test("keys are independent") {
    cache
      .flatMap(c => c.put("a", 1) *> c.put("b", 2) *> c.remove("a") *> (c.get("a"), c.get("b")).tupled)
      .assertEquals((None, Some(2)))
  }

  test("concurrent puts of distinct keys all land") {
    cache
      .flatMap { c =>
        (1 to 100).toList.parTraverse_(i => c.put(i.toString, i)) *>
          (1 to 100).toList.traverse(i => c.get(i.toString))
      }
      .map(vs => assert(vs.forall(_.isDefined)))
  }
}
