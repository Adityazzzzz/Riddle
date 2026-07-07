import { GoogleGenAI } from '@google/genai';

let aiInstance: GoogleGenAI | null = null;

function getAI() {
  if (!aiInstance) {
    aiInstance = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY || 'DUMMY_KEY_FOR_BUILD' });
  }
  return aiInstance;
}

/**
 * Streams a chat response from Gemini, yielding text chunks as they arrive.
 *
 * @param systemPrompt - The system instruction (persona + context).
 * @param userMessage - The latest message from the writer.
 * @returns An async generator that yields string chunks.
 */
export async function* streamChat(
  systemPrompt: string,
  userMessage: string
): AsyncGenerator<string> {
  try {
    const response = await getAI().models.generateContentStream({
      model: 'gemini-2.5-flash',
      contents: userMessage,
      config: { systemInstruction: systemPrompt },
    });

    for await (const chunk of response) {
      if (chunk.text) {
        yield chunk.text;
      }
    }
  } catch (error) {
    console.error('[gemini] streamChat failed:', error);
    throw error;
  }
}

/**
 * Sends a canvas screenshot to Gemini Vision and extracts the handwritten text.
 *
 * @param imageBase64 - The base64-encoded PNG image (may include data URI prefix).
 * @returns The recognised handwritten text.
 */
export async function readHandwriting(imageBase64: string): Promise<string> {
  try {
    const base64Data = imageBase64.replace(/^data:image\/\w+;base64,/, '');

    const response = await getAI().models.generateContent({
      model: 'gemini-2.5-flash',
      contents: [
        {
          role: 'user',
          parts: [
            {
              text: 'You are a handwriting recognition system. Read the handwritten text in this image and return ONLY the text content. Do not add any commentary. If you cannot read it, return the closest interpretation.',
            },
            {
              inlineData: { mimeType: 'image/png', data: base64Data },
            },
          ],
        },
      ],
    });

    return response.text ?? '';
  } catch (error) {
    console.error('[gemini] readHandwriting failed:', error);
    throw error;
  }
}

/**
 * Generates a vector embedding for the given text using text-embedding-004.
 *
 * @param text - The text to embed.
 * @returns A number array representing the embedding vector.
 */
export async function generateEmbedding(text: string): Promise<number[]> {
  try {
    const result = await getAI().models.embedContent({
      model: 'text-embedding-004',
      contents: text,
    });

    return result.embeddings?.[0]?.values ?? [];
  } catch (error) {
    console.error('[gemini] generateEmbedding failed:', error);
    throw error;
  }
}
