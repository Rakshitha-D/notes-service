package actors

import scala.concurrent.{ExecutionContext, Future}
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import models._
import services.NoteService

object NoteActor {
  sealed trait Command
  final case class CreateNoteRequest(note: Note, replyTo: akka.actor.typed.ActorRef[Response]) extends Command
  final case class GetNotesByCourse(courseId: String, replyTo: akka.actor.typed.ActorRef[Response]) extends Command

  case class Response(status: String, data: Option[Any] = None)

  def apply(noteService: NoteService)(implicit ec: ExecutionContext): Behavior[Command] = Behaviors.receive { (context, message) =>
    message match {
      case CreateNoteRequest(note, replyTo) =>
        noteService.createNote(note).map(result => replyTo ! Response("success", Some(result)))
        Behaviors.same

      case GetNotesByCourse(courseId, replyTo) =>
        noteService.getNotesByCourse(courseId).map(result => replyTo ! Response("success", Some(result)))
        Behaviors.same
    }
  }
}
