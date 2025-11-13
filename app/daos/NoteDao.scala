package daos

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.{SimpleStatement, Row}
import models.Note
import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID
import scala.jdk.CollectionConverters._

class NoteDao(session: CqlSession)(implicit ec: ExecutionContext) {

  def insertNote(note: Note): Future[Note] = Future {
    val noteId = note.noteId.getOrElse(UUID.randomUUID().toString)
    val stmt = SimpleStatement.builder(
      "INSERT INTO notes (note_id, user_id, course_id, content_id, note_text, tags) VALUES (?, ?, ?, ?, ?, ?)"
    ).addPositionalValues(
      UUID.fromString(noteId), note.userId, note.courseId, note.contentId, note.noteText, note.tags.asJava
    ).build()
    session.execute(stmt)
    note.copy(noteId = Some(noteId))
  }

  def getNotesByCourse(courseId: String): Future[List[Note]] = Future {
    val stmt = SimpleStatement.builder("SELECT * FROM notes WHERE course_id = ? ALLOW FILTERING")
      .addPositionalValue(courseId).build()
    val rs = session.execute(stmt)
    rs.iterator().asScala.map(mapRowToNote).toList
  }

  private def mapRowToNote(row: Row): Note = Note(
    noteId = Some(row.getUuid("note_id").toString),
    userId = row.getString("user_id"),
    courseId = row.getString("course_id"),
    contentId = row.getString("content_id"),
    noteText = row.getString("note_text"),
    tags = Option(row.getList("tags", classOf[String])).map(_.asScala.toList).getOrElse(List.empty)
  )

  def updateNote(noteId: String, note: Note): Future[Option[Note]] = Future {
    val stmt = SimpleStatement.builder(
      "UPDATE notes SET user_id = ?, course_id = ?, content_id = ?, note_text = ?, tags = ? WHERE note_id = ?"
    ).addPositionalValues(
      note.userId, note.courseId, note.contentId, note.noteText, note.tags.asJava, UUID.fromString(noteId)
    ).build()
    session.execute(stmt)
    Some(note.copy(noteId = Some(noteId)))
  }

  def deleteNote(noteId: String): Future[Boolean] = Future {
    val stmt = SimpleStatement.builder("DELETE FROM notes WHERE note_id = ?")
      .addPositionalValue(UUID.fromString(noteId)).build()
    session.execute(stmt)
    true
  }
}
