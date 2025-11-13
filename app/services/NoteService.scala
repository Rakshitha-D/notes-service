package services

import daos.NoteDao
import models.Note
import scala.concurrent.{ExecutionContext, Future}

class NoteService(noteDao: NoteDao)(implicit ec: ExecutionContext) {
  def createNote(note: Note): Future[Note] = noteDao.insertNote(note)
  def getNotesByCourse(courseId: String): Future[List[Note]] = noteDao.getNotesByCourse(courseId)

  def updateNote(noteId: String, note: Note): Future[Option[Note]] = noteDao.updateNote(noteId, note)

  def deleteNote(noteId: String): Future[Boolean] = noteDao.deleteNote(noteId)
}
