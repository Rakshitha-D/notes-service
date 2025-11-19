package services

import daos.NoteDao
import models.Note
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.{Json, OFormat}
import utils.RedisCache

import play.api.Logger

class NoteService(noteDao: NoteDao, redisCache: RedisCache)(implicit ec: ExecutionContext) {

  private val logger = Logger(this.getClass)

  implicit val noteFormat: OFormat[Note] = Json.format[Note]

  def createNote(note: Note): Future[Note] = {
    logger.info(s"Creating new note for course: ${note.courseId}, invalidating cache")
    redisCache.delete(s"notes:${note.courseId}")
    noteDao.insertNote(note)
  }

  def getNotesByCourse(courseId: String): Future[List[Note]] = {
    val cacheKey = s"notes:$courseId"
    logger.info(s"Fetching notes for course: $courseId, checking cache key: $cacheKey")
    
    redisCache.get(cacheKey) match {
      case Some(cached) =>
        logger.info(s"Found notes in Redis for course: $courseId")
        logger.info(s"Cached data: $cached")
        Future.successful(parseCached(cached))
      case None =>
        logger.info(s"No cached data found for course: $courseId, fetching from database")
        noteDao.getNotesByCourse(courseId).map { notes =>
          logger.info(s" fetched ${notes.length} notes from database, caching them")
          redisCache.set(cacheKey, serialize(notes))
          notes
        }
    }
  }

  private def serialize(notes: List[Note]): String = {
    Json.toJson(notes).toString()
  }

  private def parseCached(cached: String): List[Note] = {
    Json.parse(cached).as[List[Note]]
  }

  def updateNote(noteId: String, note: Note): Future[Option[Note]] =
    noteDao.updateNote(noteId, note).map { res =>
      res.foreach { _ => redisCache.delete(s"notes:${note.courseId}") }
      res
    }

  def deleteNote(noteId: String): Future[Boolean] = {
    noteDao.deleteNote(noteId).map { success =>
      if (success) {
        logger.info("Note deleted, clearing all caches")
      }
      success
    }
  }
}
