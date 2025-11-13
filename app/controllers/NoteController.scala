package controllers

import play.api.mvc._
import javax.inject._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import models._
import actors.NoteActor
import actors.NoteActor.{CreateNoteRequest, GetNotesByCourse, Response}
import services.NoteService
import daos.NoteDao
import com.datastax.oss.driver.api.core.CqlSession
import play.api.libs.json._
import java.time.Instant
import java.util.UUID

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.adapter._
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.util.Timeout

@Singleton
class NoteController @Inject()(
    cc: ControllerComponents,
    actorSystem: ActorSystem
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private val typedSystem: org.apache.pekko.actor.typed.ActorSystem[Nothing] = actorSystem.toTyped

  private val session: CqlSession = CqlSession.builder()
    .withKeyspace("notes_ks")
    .build()

  private val noteDao = new NoteDao(session)
  private val noteService = new NoteService(noteDao)

  private val noteActor = typedSystem.systemActorOf(
    NoteActor(noteService),
    "note-actor"
  )

  implicit val scheduler: Scheduler = typedSystem.scheduler
  implicit val timeout: Timeout = 5.seconds
  implicit val noteFormat: OFormat[Note] = Json.format[Note]

  private def optJs(s: Option[String]): JsValue = s.map(JsString).getOrElse(JsNull)

  private def apiEnvelope(
      result: JsValue,
      responseCode: String = "OK",
      status: String = "successful",
      err: Option[String] = None,
      errmsg: Option[String] = None,
      msgid: Option[String] = None,
      id: String = "api.notes-service.notes",
      ver: String = "1.0"
  ): JsValue = {
    Json.obj(
      "id" -> id,
      "ver" -> ver,
      "ts" -> Instant.now().toString,
      "params" -> Json.obj(
        "resmsgid" -> JsString(UUID.randomUUID().toString),
        "msgid"     -> JsString(msgid.getOrElse(UUID.randomUUID().toString)),
        "err"       -> optJs(err),
        "status"    -> JsString(status),
        "errmsg"    -> optJs(errmsg)
      ),
      "responseCode" -> responseCode,
      "result" -> result
    )
  }

  def createNote(): Action[JsValue] = Action.async(parse.json) { request =>
    val note = request.body.as[Note]
    noteActor.ask[Response](ref => CreateNoteRequest(note, ref)).map {
      case Response("success", Some(data: Note)) =>
        Ok(apiEnvelope(Json.obj("note" -> Json.toJson(data)), responseCode = "OK"))

      case Response("error", Some(msg: String)) =>
        BadRequest(apiEnvelope(Json.obj(), responseCode = "CLIENT_ERROR", status = "failed", err = Some("BadRequest"), errmsg = Some(msg)))

      case Response("error", None) =>
        BadRequest(apiEnvelope(Json.obj(), responseCode = "CLIENT_ERROR", status = "failed", err = Some("BadRequest"), errmsg = Some("Unknown error")))

      case _ =>
        InternalServerError(apiEnvelope(Json.obj(), responseCode = "SERVER_ERROR", status = "failed", err = Some("InternalServerError"), errmsg = Some("Unexpected response")))
    }
  }

  def getNotes(courseId: String): Action[AnyContent] = Action.async {
    noteActor.ask[Response](ref => GetNotesByCourse(courseId, ref)).map {
      case Response("success", Some(data: Seq[_])) =>
        val notes: Seq[Note] = data.collect { case n: Note => n }
        Ok(apiEnvelope(Json.obj("notes" -> Json.toJson(notes)), responseCode = "OK"))

      case Response("error", Some(msg: String)) =>
        BadRequest(apiEnvelope(Json.obj(), responseCode = "CLIENT_ERROR", status = "failed", err = Some("BadRequest"), errmsg = Some(msg)))

      case Response("error", None) =>
        BadRequest(apiEnvelope(Json.obj(), responseCode = "CLIENT_ERROR", status = "failed", err = Some("BadRequest"), errmsg = Some("Unknown error")))

      case _ =>
        InternalServerError(apiEnvelope(Json.obj(), responseCode = "SERVER_ERROR", status = "failed", err = Some("InternalServerError"), errmsg = Some("Unexpected response")))
    }
  }

  def updateNote(noteId: String): Action[JsValue] = Action.async(parse.json) { request =>
    val note = request.body.as[Note]
    noteService.updateNote(noteId, note).map {
      case Some(updated) =>
        Ok(apiEnvelope(Json.obj("note" -> Json.toJson(updated)), responseCode = "OK"))
      case None =>
        NotFound(apiEnvelope(Json.obj(), responseCode = "NOT_FOUND", status = "failed", err = Some("NotFound"), errmsg = Some("Note not found")))
    } recover {
      case ex =>
        InternalServerError(apiEnvelope(Json.obj(), responseCode = "SERVER_ERROR", status = "failed", err = Some("InternalServerError"), errmsg = Some(ex.getMessage)))
    }
  }

  def deleteNote(noteId: String): Action[AnyContent] = Action.async {
    noteService.deleteNote(noteId).map { success =>
      if (success)
        Ok(apiEnvelope(Json.obj("deleted" -> true, "noteId" -> noteId), responseCode = "OK"))
      else
        InternalServerError(apiEnvelope(Json.obj(), responseCode = "SERVER_ERROR", status = "failed", err = Some("InternalServerError"), errmsg = Some("Delete failed")))
    } recover {
      case ex => InternalServerError(apiEnvelope(Json.obj(), responseCode = "SERVER_ERROR", status = "failed", err = Some("InternalServerError"), errmsg = Some(ex.getMessage)))
    }
  }
}
