"use client";

import { useState, useRef, useCallback, useEffect } from "react";
import WritingCanvas, { WritingCanvasHandle } from "@/components/WritingCanvas";
import StreamingReply from "@/components/StreamingReply";
import MagicalParticles from "@/components/MagicalParticles";

interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
  timestamp?: string;
  status: "visible" | "fading" | "faded";
}

type RiddleState = "hidden" | "writing" | "visible" | "fading";

export default function DiaryPage() {
  const [isProcessing, setIsProcessing] = useState(false);
  const [streamedText, setStreamedText] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);

  // Tom Riddle reply state
  const [riddleState, setRiddleState] = useState<RiddleState>("hidden");
  const [currentResponse, setCurrentResponse] = useState("");

  // Conversation history (loaded from localStorage)
  const [history, setHistory] = useState<Message[]>([]);

  const canvasRef = useRef<WritingCanvasHandle>(null);
  const fadeTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  // Load history from localStorage on mount
  useEffect(() => {
    try {
      const savedHistory = localStorage.getItem("notewise_diary_history");
      if (savedHistory) {
        // Mark everything as faded initially so it looks like older ghost text
        const parsed = JSON.parse(savedHistory) as Message[];
        const faded = parsed.map((m) => ({ ...m, status: "faded" as const }));
        setHistory(faded);
      }
    } catch (err) {
      console.error("Failed to load history:", err);
    }
  }, []);

  // Save history to localStorage when changed
  const saveHistory = useCallback((newHistory: Message[]) => {
    try {
      localStorage.setItem("notewise_diary_history", JSON.stringify(newHistory));
    } catch (err) {
      console.error("Failed to save history:", err);
    }
  }, []);

  // Sync streamed text with response display
  useEffect(() => {
    if (streamedText) {
      setCurrentResponse(streamedText);
      setRiddleState("writing");
    }
  }, [streamedText]);

  // Handle auto-fading after Riddle finishes writing
  useEffect(() => {
    if (riddleState === "visible") {
      fadeTimeoutRef.current = setTimeout(() => {
        setRiddleState("fading");
      }, 5500); // Display for 5.5 seconds before fading
    } else if (riddleState === "fading") {
      const clearTimer = setTimeout(() => {
        setCurrentResponse("");
        setRiddleState("hidden");
      }, 2000);
      return () => clearTimeout(clearTimer);
    }

    return () => {
      if (fadeTimeoutRef.current) {
        clearTimeout(fadeTimeoutRef.current);
      }
    };
  }, [riddleState]);

  /** Stream reply from Tom Riddle */
  const sendToDiary = useCallback(
    async (imageData: string, tempUserMsgId: string) => {
      setIsProcessing(true);
      setStreamedText("");
      setIsStreaming(true);
      setRiddleState("hidden");
      setCurrentResponse("");

      // Map current history to format expected by API
      const formattedHistory = history.map((m) => ({
        role: m.role,
        content: m.content,
      }));

      try {
        const res = await fetch("/api/chat", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            imageData,
            history: formattedHistory,
          }),
        });

        if (!res.ok) {
          const err = await res.json().catch(() => ({ error: "Unknown error" }));
          throw new Error(err.error || "Request failed");
        }

        const reader = res.body?.getReader();
        if (!reader) throw new Error("No response stream");

        const decoder = new TextDecoder();
        let accumulated = "";
        let buffer = "";
        let finalTranscription = "";

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split("\n");
          buffer = lines.pop() || "";

          for (const line of lines) {
            if (!line.startsWith("data: ")) continue;
            const data = line.slice(6).trim();

            if (data === "[DONE]") {
              setIsStreaming(false);
              setRiddleState("visible");
              setStreamedText("");

              // Update user message with the parsed transcription
              const transcription = finalTranscription || "✎ (handwritten entry)";
              
              setHistory((prev) => {
                const updatedUser = prev.map((m) =>
                  m.id === tempUserMsgId ? { ...m, content: transcription } : m
                );
                
                const updatedWithRiddle: Message[] = [
                  ...updatedUser,
                  {
                    id: crypto.randomUUID(),
                    role: "assistant",
                    content: accumulated.trim(),
                    timestamp: new Date().toISOString(),
                    status: "visible",
                  },
                ];

                saveHistory(updatedWithRiddle);
                return updatedWithRiddle;
              });

              continue;
            }

            try {
              const parsed = JSON.parse(data);
              if (parsed.error) {
                throw new Error(parsed.error);
              }
              if (parsed.transcription) {
                finalTranscription = parsed.transcription;
              }
              if (parsed.token) {
                accumulated += parsed.token;
                setStreamedText(accumulated);
              }
            } catch {
              // Ignore malformed chunks
            }
          }
        }
      } catch (err) {
        console.error("Diary error:", err);
        setIsStreaming(false);
        setRiddleState("visible");
        setStreamedText("");
        
        // Clean up temporary message on error
        setHistory((prev) => prev.filter((m) => m.id !== tempUserMsgId));
        
        setCurrentResponse("Tom does not feel like answering right now.");
      } finally {
        setIsProcessing(false);
      }
    },
    [history, saveHistory]
  );

  /** Handle canvas handwriting submission */
  const handleCanvasSubmit = useCallback(
    async (imageData: string) => {
      if (isProcessing) return;

      // Clear any active Riddle fade timer
      if (fadeTimeoutRef.current) {
        clearTimeout(fadeTimeoutRef.current);
      }
      setRiddleState("hidden");

      // 1. Create a temporary placeholder message in history
      const tempUserMsgId = crypto.randomUUID();
      const userMsg: Message = {
        id: tempUserMsgId,
        role: "user",
        content: "✎ (handwriting)",
        timestamp: new Date().toISOString(),
        status: "visible",
      };
      setHistory((prev) => [...prev, userMsg]);

      // 2. Trigger stylus drawing fade-out
      await canvasRef.current?.fadeOut();

      // 3. Mark the temporary user message as fading
      setHistory((prev) =>
        prev.map((m) =>
          m.id === tempUserMsgId ? { ...m, status: "fading" as const } : m
        )
      );

      // 4. Mark as faded after the fade animation completes
      setTimeout(() => {
        setHistory((prev) =>
          prev.map((m) =>
            m.id === tempUserMsgId ? { ...m, status: "faded" as const } : m
          )
        );
      }, 2500);

      // 5. Send captured drawing to server
      await sendToDiary(imageData, tempUserMsgId);
    },
    [isProcessing, sendToDiary]
  );

  return (
    <div className="relative h-screen w-screen bg-[#ecebe6] overflow-hidden select-none touch-none">
      {/* Ambient magical particles */}
      <MagicalParticles count={8} />

      {/* Paper texture vignette */}
      <div
        className="absolute inset-0 pointer-events-none z-10"
        style={{
          boxShadow: "inset 0 0 100px rgba(0, 0, 0, 0.04)",
          background: "radial-gradient(circle, transparent 60%, rgba(0,0,0,0.02) 100%)",
        }}
      />

      {/* Stylus Canvas Overlay */}
      <div className="absolute inset-0 z-20 pointer-events-auto">
        <WritingCanvas
          ref={canvasRef}
          onSubmit={handleCanvasSubmit}
          disabled={isProcessing}
          color="#222228"
          thickness={2.5}
          tool="pen"
          autoSubmitDelay={1600}
        />
      </div>

      {/* Riddle reply stream */}
      {currentResponse && (
        <div
          className={`absolute inset-0 flex items-center justify-center pointer-events-none z-30 px-10 transition-all duration-[2000ms] ${
            riddleState === "fading" ? "opacity-0 blur-[2px]" : "opacity-100 blur-0"
          }`}
        >
          <div className="max-w-xl text-center">
            <StreamingReply text={currentResponse} isStreaming={isStreaming} />
          </div>
        </div>
      )}
    </div>
  );
}
