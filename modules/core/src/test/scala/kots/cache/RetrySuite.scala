package kots.cache

import cats.effect.IO
import cats.effect.kernel.Ref
import munit.CatsEffectSuite

final class RetrySuite extends CatsEffectSuite {

  private def attempt(calls: Ref[IO, Int], winsAt: Int): IO[Option[String]] =
    calls.modify(seen => (seen + 1, seen + 1)).map(seen => Option.when(seen >= winsAt)("won"))

  test("a compare-and-set that wins at once spends one attempt") {
    for {
      calls <- Ref.of[IO, Int](0)
      won   <- Retry.attempts(4).cas(attempt(calls, 1))
      spent <- calls.get
    } yield {
      assertEquals(won, "won")
      assertEquals(spent, 1)
    }
  }

  test("a compare-and-set that wins late returns its value") {
    for {
      calls <- Ref.of[IO, Int](0)
      won   <- Retry.attempts(8).cas(attempt(calls, 3))
      spent <- calls.get
    } yield {
      assertEquals(won, "won")
      assertEquals(spent, 3)
    }
  }

  test("a compare-and-set that never wins fails with its budget spent") {
    for {
      calls  <- Ref.of[IO, Int](0)
      outcome <- Retry.attempts(3).cas(attempt(calls, Int.MaxValue)).attempt
      spent  <- calls.get
    } yield {
      assertEquals(outcome, Left(CasExhausted(3)))
      assertEquals(spent, 3)
    }
  }

  test("an exhausted budget carries no key or value contents") {
    val error = CasExhausted(3)
    assertEquals(error.getMessage.contains("won"), false)
  }

  test("the attempt budget is never below one") {
    assertEquals(Retry.attempts(0).maxAttempts, 1)
    assertEquals(Retry.attempts(-5).maxAttempts, 1)
  }

  test("the default budget is positive") {
    assert(Retry.default.maxAttempts > 0)
  }
}
