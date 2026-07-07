import { NextRequest } from 'next/server';
import { streamChat, readHandwriting } from '@/lib/gemini';
import { buildContextPrompt } from '@/lib/prompts';

export const maxDuration = 60;

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

export async function POST(req: NextRequest) {
  const encoder = new TextEncoder();

  try {
    const body = await req.json();
    const { message, imageData, history = [] } = body as {
      message?: string;
      imageData?: string;
      history?: ChatMessage[];
    };

    // 1. Transcribe handwriting image if provided
    let userText = message || '';

    if (imageData && !userText) {
      try {
        userText = await readHandwriting(imageData);
      } catch (err) {
        console.error('Handwriting recognition failed:', err);
        return new Response(
          JSON.stringify({ error: 'Tom was unable to read your ink. Write clearly.' }),
          { status: 400, headers: { 'Content-Type': 'application/json' } }
        );
      }
    }

    if (!userText.trim()) {
      return new Response(
        JSON.stringify({ error: 'No writing detected.' }),
        { status: 400, headers: { 'Content-Type': 'application/json' } }
      );
    }

    // 2. Build the system prompt using client-supplied history
    // Since we're local, history is passed in. We extract the last few secrets
    const pastSecrets = history
      .filter((m) => m.role === 'user' && m.content.toLowerCase().includes('secret'))
      .map((m) => m.content);

    const systemPrompt = buildContextPrompt(pastSecrets, history);

    // 3. Create the Server-Sent Events stream
    const stream = new ReadableStream({
      async start(controller) {
        try {
          const chatStream = streamChat(systemPrompt, userText);

          for await (const chunk of chatStream) {
            const data = JSON.stringify({ token: chunk });
            controller.enqueue(encoder.encode(`data: ${data}\n\n`));
          }

          // Send finish signal
          const doneData = JSON.stringify({ token: '', done: true, transcription: userText });
          controller.enqueue(encoder.encode(`data: ${doneData}\n\n`));
          controller.enqueue(encoder.encode(`data: [DONE]\n\n`));
        } catch (err) {
          console.error('Gemini stream error:', err);
          const errorData = JSON.stringify({
            error: 'The ink is blurring... try writing again.',
          });
          controller.enqueue(encoder.encode(`data: ${errorData}\n\n`));
        } finally {
          controller.close();
        }
      },
    });

    return new Response(stream, {
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache, no-store, must-revalidate',
        Connection: 'keep-alive',
        'X-Accel-Buffering': 'no',
      },
    });
  } catch (err) {
    console.error('Stateless Chat API error:', err);
    return new Response(
      JSON.stringify({ error: 'The diary is currently silent.' }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    );
  }
}
