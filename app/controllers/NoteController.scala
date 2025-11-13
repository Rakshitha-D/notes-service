package controllers

import play.api.mvc._
import javax.inject._
import akka.actor.ActorSystem                      // classic ActorSystem injected by Play/Guice
import akka.actor.typed.scaladsl.adapter._         // adapter to convert classic -> typed
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.Scheduler
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import models._
import actors.NoteActor
import actors.NoteActor.{CreateNoteRequest, GetNotesByCourse, Response}
import services.NoteService
import daos.NoteDao
import com.datastax.oss.driver.api.core.CqlSession
import play.api.libs.json._

@Singleton
class NoteController @Inject()(
    cc: ControllerComponents,
    actorSystem: ActorSystem                       // classic ActorSystem here
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  // convert classic to typed
  private val typedSystem: akka.actor.typed.ActorSystem[Nothing] = actorSystem.toTyped

  // Cassandra session setup
  private val session: CqlSession = CqlSession.builder()
    .withKeyspace("notes_ks")
    .build()

  // Single DAO and Service instances reused by controller and actor
  private val noteDao = new NoteDao(session)
  private val noteService = new NoteService(noteDao)

  // Actor initialization (use the typed system)
  private val noteActor = typedSystem.systemActorOf(
    NoteActor(noteService),
    "note-actor"
  )

  // Required implicits
  implicit val scheduler: Scheduler = typedSystem.scheduler
  implicit val timeout: Timeout = 5.seconds
  implicit val noteFormat: OFormat[Note] = Json.format[Note]

  /** Create a note */
  def createNote(): Action[JsValue] = Action.async(parse.json) { request =>
    val note = request.body.as[Note]
    noteActor.ask[Response](ref => CreateNoteRequest(note, ref)).map {
      case Response("success", Some(data: Note)) =>
        Ok(Json.obj("status" -> "success", "data" -> Json.toJson(data)))

      case Response("error", Some(msg: String)) =>
        BadRequest(Json.obj("error" -> msg))

      case Response("error", None) =>
        BadRequest(Json.obj("error" -> "Unknown error"))

      case _ =>
        InternalServerError(Json.obj("error" -> "Unexpected response"))
    }
  }

  /** Get notes by course ID */
  def getNotes(courseId: String): Action[AnyContent] = Action.async {
    noteActor.ask[Response](ref => GetNotesByCourse(courseId, ref)).map {
      // Accept any Seq at runtime, then filter/cast elements to Note so Play finds the Writes[Note] -> Writes[Seq[Note]]
      case Response("success", Some(data: Seq[_])) =>
        val notes: Seq[Note] = data.collect { case n: Note => n }
        Ok(Json.obj("status" -> "success", "data" -> Json.toJson(notes)))

      case Response("error", Some(msg: String)) =>
        BadRequest(Json.obj("error" -> msg))

      case Response("error", None) =>
        BadRequest(Json.obj("error" -> "Unknown error"))

      case _ =>
        InternalServerError(Json.obj("error" -> "Unexpected response"))
    }
  }

  /** Update a note (replace entire note body). Caller must send full Note JSON; noteId path param is authoritative. */
  def updateNote(noteId: String): Action[JsValue] = Action.async(parse.json) { request =>
    val note = request.body.as[Note]
    // Ensure noteId in path is set on returned object
    noteService.updateNote(noteId, note).map {
      case Some(updated) => Ok(Json.obj("status" -> "success", "data" -> Json.toJson(updated)))
      case None => NotFound(Json.obj("error" -> "Note not found"))
    } recover {
      case ex =>
        InternalServerError(Json.obj("error" -> ex.getMessage))
    }
  }

  /** Delete a note by ID */
  def deleteNote(noteId: String): Action[AnyContent] = Action.async {
    noteService.deleteNote(noteId).map { success =>
      if (success) Ok(Json.obj("status" -> "success")) else InternalServerError(Json.obj("error" -> "Delete failed"))
    } recover {
      case ex => InternalServerError(Json.obj("error" -> ex.getMessage))
    }
  }
}
