-- Create keyspace
CREATE KEYSPACE IF NOT EXISTS notes_ks
WITH REPLICATION = {
  'class': 'SimpleStrategy',
  'replication_factor': 1
};

-- Use the keyspace
USE notes_ks;

-- Create notes table
CREATE TABLE IF NOT EXISTS notes (
  note_id UUID PRIMARY KEY,
  user_id TEXT,
  course_id TEXT,
  content_id TEXT,
  note_text TEXT,
  tags LIST<TEXT>
);

-- Create secondary index on course_id for efficient querying
CREATE INDEX IF NOT EXISTS notes_course_id_idx ON notes (course_id);
