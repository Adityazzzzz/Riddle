-- ============================================
-- Tom Riddle's Diary — Horcrux Memory Schema
-- Run this in your Supabase SQL Editor
-- ============================================

-- Enable vector extension for semantic search
CREATE EXTENSION IF NOT EXISTS vector;

-- Diary entries with semantic embeddings
CREATE TABLE IF NOT EXISTS diary_entries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  role TEXT NOT NULL CHECK (role IN ('user', 'assistant')),
  content TEXT NOT NULL,
  embedding VECTOR(768),  -- Gemini text-embedding-004 outputs 768 dimensions
  created_at TIMESTAMPTZ DEFAULT now()
);

-- HNSW index for fast similarity search
CREATE INDEX IF NOT EXISTS diary_entries_embedding_idx 
  ON diary_entries USING hnsw (embedding vector_cosine_ops);

-- Index for recent history queries
CREATE INDEX IF NOT EXISTS diary_entries_created_at_idx 
  ON diary_entries (created_at DESC);

-- Function for semantic similarity search
CREATE OR REPLACE FUNCTION match_diary_entries(
  query_embedding VECTOR(768),
  match_threshold FLOAT DEFAULT 0.5,
  match_count INT DEFAULT 5
)
RETURNS TABLE (
  id UUID, 
  role TEXT, 
  content TEXT, 
  created_at TIMESTAMPTZ, 
  similarity FLOAT
)
LANGUAGE plpgsql AS $$
BEGIN
  RETURN QUERY
  SELECT
    d.id, 
    d.role, 
    d.content, 
    d.created_at,
    1 - (d.embedding <=> query_embedding) AS similarity
  FROM diary_entries d
  WHERE d.role = 'user'
    AND 1 - (d.embedding <=> query_embedding) > match_threshold
  ORDER BY d.embedding <=> query_embedding
  LIMIT match_count;
END;
$$;
