package kots.cache

import cats.syntax.invariant._
import munit.FunSuite

final class CodecSuite extends FunSuite {

  private final case class UserId(value: Int)

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

  test("imap derives a codec that round-trips") {
    val codec = Codec.int.imap(UserId.apply)(_.value)
    roundTrips(codec, List(UserId(0), UserId(42), UserId(-7), UserId(Int.MaxValue)))
  }

  test("imap keeps the base codec's decode failures") {
    val codec = Codec.int.imap(UserId.apply)(_.value)
    assertEquals(codec.decode("not-a-number"), Codec.int.decode("not-a-number").map(UserId.apply))
  }

  test("imap composes: two maps equal one composed map") {
    val once = Codec.int.imap(UserId.apply)(_.value).imap(_.value)(UserId.apply)
    roundTrips(once, List(1, -1))
    assertEquals(once.encode(5), Codec.int.encode(5))
  }

  test("a decode error never carries the input contents") {
    val secret = "customer-1234-secret"
    Codec.int.decode(secret) match {
      case Left(e)  => assert(!e.description.contains(secret))
      case Right(_) => fail("decode should not succeed")
    }
  }
}
