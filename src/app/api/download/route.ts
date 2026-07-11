import { NextRequest, NextResponse } from 'next/server';
import fs from 'fs';
import path from 'path';

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const version = searchParams.get('version') || 'v1.0.4';
  const key = searchParams.get('key');
  const secret = process.env.DEV_SECRET || 'you-know-who-secret-2026';

  // Public version (v1.0.4) is always downloadable without any secret key
  if (version === 'v1.0.4') {
    const publicPath = path.join(process.cwd(), 'public/releases/riddle-diary-v1.0.4.apk');
    if (!fs.existsSync(publicPath)) {
      return new NextResponse('File not found', { status: 404 });
    }
    const fileBuffer = fs.readFileSync(publicPath);
    return new NextResponse(fileBuffer, {
      headers: {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Disposition': 'attachment; filename=riddle-diary-v1.0.4.apk',
      },
    });
  }

  // Private versions require authorization key check
  if (key !== secret) {
    return new NextResponse('Unauthorized', { status: 401 });
  }

  const privatePath = path.join(process.cwd(), `releases/riddle-diary-${version}.apk`);
  if (!fs.existsSync(privatePath)) {
    return new NextResponse(`Version ${version} not found`, { status: 404 });
  }

  const fileBuffer = fs.readFileSync(privatePath);
  return new NextResponse(fileBuffer, {
    headers: {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Disposition': `attachment; filename=riddle-diary-${version}.apk`,
    },
  });
}
