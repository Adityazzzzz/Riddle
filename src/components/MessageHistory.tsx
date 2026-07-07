'use client';

import React, { useEffect, useRef, useMemo } from 'react';

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp?: string;
  status: 'visible' | 'fading' | 'faded';
}

interface MessageHistoryProps {
  messages: Message[];
  className?: string;
}

const MessageHistory: React.FC<MessageHistoryProps> = ({
  messages,
  className = '',
}) => {
  const bottomRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to bottom when new messages arrive
  useEffect(() => {
    if (bottomRef.current) {
      bottomRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages.length]);

  // Memoize messages for performance
  const renderedMessages = useMemo(() => {
    return messages.map((message) => {
      const isUser = message.role === 'user';

      // Status-based styles
      let statusClasses = '';
      let statusStyles: React.CSSProperties = {};

      switch (message.status) {
        case 'fading':
          statusClasses = 'message-fading';
          break;
        case 'faded':
          statusStyles = {
            opacity: 0.12, // User's faded ink is extremely faint, almost gone
            filter: 'blur(0.8px)',
          };
          break;
        case 'visible':
        default:
          break;
      }

      return (
        <div
          key={message.id}
          className={`transition-all duration-700 w-full ${statusClasses} mb-6`}
          style={statusStyles}
        >
          {isUser ? (
            /* User Handwriting (Faded Ink) */
            <div className="pl-4 pr-12 text-left">
              <p className="font-[family-name:var(--font-cedarville)] text-stone-400/80 text-xl leading-[32px] italic select-none">
                {message.content}
              </p>
            </div>
          ) : (
            /* Tom Riddle's Handwriting */
            <div className="pl-12 pr-4 text-left">
              <p className="font-[family-name:var(--font-caveat)] text-stone-800 text-2xl font-medium leading-[32px] tracking-wide">
                {message.content}
              </p>
            </div>
          )}
        </div>
      );
    });
  }, [messages]);

  if (messages.length === 0) return null;

  return (
    <div className={`relative ${className}`}>
      {/* fadeAway animation */}
      <style>{`
        @keyframes fadeAway {
          from {
            opacity: 1;
            filter: blur(0);
          }
          to {
            opacity: 0.12;
            filter: blur(0.8px);
          }
        }
        .message-fading {
          animation: fadeAway 2.5s cubic-bezier(0.25, 1, 0.5, 1) forwards;
        }
      `}</style>

      <div className="flex flex-col w-full">
        {renderedMessages}
        <div ref={bottomRef} className="h-0 w-0 shrink-0" />
      </div>
    </div>
  );
};

export default MessageHistory;
