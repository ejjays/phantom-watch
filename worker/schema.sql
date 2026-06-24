CREATE TABLE IF NOT EXISTS event (
  ts INTEGER NOT NULL,
  src TEXT NOT NULL DEFAULT 'app',
  v TEXT,
  cfg INTEGER,
  kind TEXT,
  tmdb TEXT,
  season TEXT,
  episode TEXT,
  ms INTEGER,
  formats INTEGER,
  pick TEXT,
  legs TEXT,
  h INTEGER,
  ens INTEGER,
  ena INTEGER,
  hls INTEGER
);

CREATE INDEX IF NOT EXISTS event_ts ON event(ts);

CREATE TABLE IF NOT EXISTS state (k TEXT PRIMARY KEY, v TEXT NOT NULL);
