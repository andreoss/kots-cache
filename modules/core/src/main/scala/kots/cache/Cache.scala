package kots.cache

/** Effect-polymorphic cache algebra over typed keys and values. */
trait Cache[F[_], K, V] {
  def get(key: K): F[Option[V]]
  def put(key: K, value: V): F[Unit]
  def remove(key: K): F[Unit]
  def clear: F[Unit]
}
