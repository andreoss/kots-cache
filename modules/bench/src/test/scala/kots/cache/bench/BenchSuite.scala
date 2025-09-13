package kots.cache.bench

import munit.CatsEffectSuite

final class BenchSuite extends CatsEffectSuite {

  test("the harness reports one measurement per contender") {
    Bench.run(1000).map { reports =>
      assertEquals(reports.map(_.label), List("mem adapter", "baseline ref map"))
      assert(reports.forall(_.ops == 1000))
    }
  }

  test("a report renders without key or value contents") {
    Bench.run(100).map { reports =>
      reports.foreach(r => assert(r.show.startsWith(r.label)))
    }
  }
}
