package actors

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.Behavior
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

abstract class BaseActor[T] {
  def onReceive(msg: T): Future[Any]
  def behavior: Behavior[T] = Behaviors.receiveMessage[T] { msg =>
    onReceive(msg)
    Behaviors.same
  }
}
