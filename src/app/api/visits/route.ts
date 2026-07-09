import { NextResponse } from 'next/server';

// In-memory fallback for local development (will reset when server restarts)
let localVisits = 142; // Start with a magical number

export async function GET() {
  const kvUrl = process.env.KV_REST_API_URL;
  const kvToken = process.env.KV_REST_API_TOKEN;

  if (kvUrl && kvToken) {
    try {
      // Direct REST call to Vercel KV / Upstash Redis to increment the visits key
      const response = await fetch(`${kvUrl}/pipeline`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${kvToken}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify([
          ['INCR', 'riddle_diary_visits']
        ]),
        cache: 'no-store'
      });

      const data = await response.json();
      // Upstash pipeline response format: [[null, newValue]] or [{result: newValue}]
      if (Array.isArray(data) && data[0] && typeof data[0][1] === 'number') {
        return NextResponse.json({ visits: data[0][1] });
      }
    } catch (e) {
      console.error('Vercel KV failed, using local memory fallback:', e);
    }
  }

  // Fallback if Vercel KV is not linked (local development)
  localVisits++;
  return NextResponse.json({ visits: localVisits });
}
