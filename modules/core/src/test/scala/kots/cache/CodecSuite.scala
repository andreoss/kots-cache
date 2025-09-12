package kots.cache

import munit.FunSuite

final class CodecSuite extends FunSuite {

  private def roundTrips[A](codec: Codec[A], values: List[A]): Unit =
    values.foreach(a => assertEquals(codec.decode(codec.encode(a)), Right(a)))

  test("int codec round-trips") {
    roundTrips(Codec.int, List(0, 1, -1, Int.MaxValue, Int.MinValue))
  }

  test("string codec round-trips") {
    roundTrips(Codec.string, List("", "plain", "with space", "юникод", "line\nbreak"))
  }

  test("malformed int input fails to decode") {
    assert(Codec.int.decode("not-a-number").isLeft)
    assert(Codec.int.decode("").isLeft)
    assert(Codec.int.decode("1.5").isLeft)
  }

  test("a decode error never carries the input contents") {
    val secret = "customer-1234-secret"
    Codec.int.decode(secret) match {
      case Left(e)  => assert(!e.description.contains(secret))
      case Right(_) => fail("decode should not succeed")
    }
  }
}
