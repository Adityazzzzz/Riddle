'use client';

import React, { useMemo } from 'react';

interface StreamingReplyProps {
  text: string;
  isStreaming: boolean;
  className?: string;
}

const StreamingReply: React.FC<StreamingReplyProps> = ({
  text,
  isStreaming,
  className = '',
}) => {
  // Split text into characters
  const characters = useMemo(() => {
    if (!text) return [];
    return text.split('');
  }, [text]);

  if (!text && !isStreaming) return null;

  return (
    <div className={`relative ${className}`}>
      {/* Keyframe animation definition */}
      <style>{`
        @keyframes inkAppear {
          from {
            opacity: 0;
            filter: blur(1.5px);
            transform: translateY(2px);
          }
          to {
            opacity: 1;
            filter: blur(0);
            transform: translateY(0);
          }
        }
        .ink-char {
          display: inline;
          opacity: 0;
          animation: inkAppear 0.4s ease-out forwards;
        }
      `}</style>

      <div className="font-[family-name:var(--font-caveat)] text-stone-700 text-2xl leading-relaxed whitespace-pre-wrap">
        {characters.map((char, index) => {
          if (char === '\n') {
            return <br key={`br-${index}`} />;
          }

          return (
            <span
              key={`char-${index}`}
              className="ink-char"
              style={{
                animationDelay: `${index * 0.015}s`,
              }}
            >
              {char}
            </span>
          );
        })}

        {/* Blinking cursor while streaming */}
        {isStreaming && (
          <span
            className="inline-block w-[2.5px] h-[1.1em] bg-amber-500/80 ml-0.5 align-middle animate-pulse rounded-full"
            aria-hidden="true"
          />
        )}
      </div>
    </div>
  );
};

export default StreamingReply;
