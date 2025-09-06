package kots.cache

import cats.effect.IO
import cats.effect.testkit.TestControl
import munit.CatsEffectSuite

import scala.concurrent.duration._

final class ClockPortSuite extends CatsEffectSuite {

  test("monotonic time is fully controlled by the test clock") {
    TestControl.executeEmbed {
      for {
        t0 <- IO.monotonic
        _ <- IO.sleep(1.hour)
        t1 <- IO.monotonic
      } yield assertEquals(t1 - t0, 1.hour)
    }
  }

  test("controlled time is independent of wall-clock duration") {
    IO.realTime.flatMap { wallStart =>
      TestControl.executeEmbed(IO.sleep(365.days).as(())) *>
        IO.realTime.map(wallEnd => assert((wallEnd - wallStart) < 1.minute))
    }
  }
}
