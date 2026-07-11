import { NextRequest, NextResponse } from 'next/server';

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const key = searchParams.get('key');
  const secret = process.env.DEV_SECRET || 'you-know-who-secret-2026';

  if (key === secret) {
    return NextResponse.json({ valid: true });
  }
  return NextResponse.json({ valid: false });
}
