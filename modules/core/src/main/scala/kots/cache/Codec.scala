package kots.cache

/** Codec between a typed value and its text form; decoding is a parse. */
trait Codec[A] {
  def encode(value: A): String
  def decode(text: String): Either[CodecError, A]
}

/** Decode failure; the description never carries input contents. */
final case class CodecError(description: String)

object Codec {

  val string: Codec[String] =
    new Codec[String] {
      def encode(value: String): String = value
      def decode(text: String): Either[CodecError, String] = Right(text)
    }

  val int: Codec[Int] =
    new Codec[Int] {
      def encode(value: Int): String = value.toString
      def decode(text: String): Either[CodecError, Int] =
        text.toIntOption.toRight(CodecError("not a base-10 int"))
    }
}
