import { supabaseAdmin } from './supabase';
import { generateEmbedding } from './gemini';

/**
 * Stores a diary entry alongside its vector embedding for future recall.
 *
 * @param role - Whether this entry is from the 'user' (writer) or 'assistant' (diary).
 * @param content - The text content of the entry.
 */
export async function storeEntry(
  role: 'user' | 'assistant',
  content: string
): Promise<void> {
  try {
    const embedding = await generateEmbedding(content);

    const { error } = await supabaseAdmin.from('diary_entries').insert({
      role,
      content,
      embedding,
    });

    if (error) {
      console.error('[memory] Failed to store diary entry:', error.message);
    }
  } catch (error) {
    console.error('[memory] storeEntry failed:', error);
  }
}

/**
 * Recalls previously confided secrets that are semantically similar to the query.
 * Uses a Supabase RPC function (`match_diary_entries`) backed by pgvector.
 *
 * @param query - The text to find similar memories for.
 * @param limit - Maximum number of results to return (default 5).
 * @returns An array of matching entries with content, timestamp, and similarity score.
 */
export async function recallSecrets(
  query: string,
  limit = 5
): Promise<{ content: string; created_at: string; similarity: number }[]> {
  try {
    const queryEmbedding = await generateEmbedding(query);

    const { data, error } = await supabaseAdmin.rpc('match_diary_entries', {
      query_embedding: queryEmbedding,
      match_count: limit,
    });

    if (error) {
      console.error('[memory] recallSecrets RPC failed:', error.message);
      return [];
    }

    return data ?? [];
  } catch (error) {
    console.error('[memory] recallSecrets failed:', error);
    return [];
  }
}

/**
 * Retrieves the most recent diary entries in chronological order.
 *
 * @param limit - Maximum number of entries to retrieve (default 10).
 * @returns An array of entries with role and content, oldest first.
 */
export async function getRecentHistory(
  limit = 10
): Promise<{ role: string; content: string }[]> {
  try {
    const { data, error } = await supabaseAdmin
      .from('diary_entries')
      .select('role, content')
      .order('created_at', { ascending: false })
      .limit(limit);

    if (error) {
      console.error('[memory] getRecentHistory failed:', error.message);
      return [];
    }

    return (data ?? []).reverse();
  } catch (error) {
    console.error('[memory] getRecentHistory failed:', error);
    return [];
  }
}
