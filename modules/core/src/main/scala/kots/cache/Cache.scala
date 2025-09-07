package kots.cache

/** Effect-polymorphic cache algebra over typed keys and values. */
trait Cache[F[_], K, V] {
  def get(key: K): F[Option[V]]
  def put(key: K, value: V): F[Unit]
  def modify[A](key: K)(f: Option[V] => (Option[V], A)): F[A]
  def remove(key: K): F[Unit]
  def clear: F[Unit]
}
