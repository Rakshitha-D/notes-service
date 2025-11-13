package models

case class Note(
  noteId: Option[String],
  userId: String,
  courseId: String,
  contentId: String,
  noteText: String,
  tags: List[String]
)

case class CreateNoteRequest(note: Note)
case class Response(status: String, data: Option[Any] = None)
