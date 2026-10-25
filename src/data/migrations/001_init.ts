export default `
CREATE TABLE sessions (
  id TEXT PRIMARY KEY NOT NULL,
  task TEXT NOT NULL,
  mode TEXT NOT NULL,
  allowlist TEXT NOT NULL,
  started_at INTEGER NOT NULL,
  planned_end_at INTEGER NOT NULL,
  ended_at INTEGER,
  end_type TEXT
);

CREATE TABLE exit_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id TEXT,
  ts INTEGER NOT NULL,
  choice TEXT NOT NULL CHECK (choice IN ('break', 'end', 'emergency')),
  reason TEXT NOT NULL,
  word_count INTEGER NOT NULL
);
CREATE INDEX exit_events_ts ON exit_events (ts);

CREATE TABLE block_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id TEXT,
  ts INTEGER NOT NULL,
  pkg TEXT NOT NULL,
  reason TEXT NOT NULL,
  source TEXT NOT NULL
);
CREATE INDEX block_events_session ON block_events (session_id);
`;
