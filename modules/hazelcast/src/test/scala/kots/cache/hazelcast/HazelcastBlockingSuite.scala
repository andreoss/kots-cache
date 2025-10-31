package kots.cache.hazelcast

import cats.effect.IO
import cats.syntax.all._
import com.hazelcast.map.IMap
import munit.CatsEffectSuite

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.util.concurrent.ConcurrentLinkedQueue

final class HazelcastBlockingSuite extends CatsEffectSuite {

  private def shape(name: String): String = name.replaceAll("[0-9]+$", "")

  private def recording(threads: ConcurrentLinkedQueue[String]): IMap[String, Int] =
    Proxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array(classOf[IMap[_, _]]),
        new InvocationHandler {
          def invoke(proxy: AnyRef, method: Method, args: Array[AnyRef]): AnyRef = {
            threads.add(Thread.currentThread().getName)
            if (method.getReturnType == java.lang.Boolean.TYPE) java.lang.Boolean.TRUE
            else null
          }
        },
      )
      .asInstanceOf[IMap[String, Int]]

  test("every algebra call reaches the client on the blocking pool") {
    val threads = new ConcurrentLinkedQueue[String]
    val cache = HazelcastCache.of[IO, String, Int](recording(threads))
    for {
      compute <- IO(Thread.currentThread().getName)
      _ <- cache.get("a")
      _ <- cache.put("a", 1)
      _ <- cache.modify("a")(_ => (Some(2), ()))
      _ <- cache.remove("a")
      _ <- cache.clear
      seen <- IO(threads.toArray.toList.map(name => shape(name.toString)))
      blocking <- IO.blocking(Thread.currentThread().getName)
    } yield {
      assertEquals(seen.size, 6)
      assertEquals(seen.distinct, List(shape(blocking)))
      assertNotEquals(seen.head, shape(compute))
    }
  }
}
