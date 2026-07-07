import { createClient } from '@supabase/supabase-js';

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL || 'https://dummy-project.supabase.co';
const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || 'dummy-anon-key';
const supabaseServiceKey = process.env.SUPABASE_SERVICE_ROLE_KEY || 'dummy-service-key';

/** Public Supabase client using the anon key — safe for client-side reads. */
export const supabase = createClient(supabaseUrl, supabaseAnonKey);

/** Admin Supabase client using the service role key — server-side only, bypasses RLS. */
export const supabaseAdmin = createClient(supabaseUrl, supabaseServiceKey);
