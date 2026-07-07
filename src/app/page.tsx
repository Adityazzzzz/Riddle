"use client";

import { useState, useRef, useCallback, useEffect } from "react";
import WritingCanvas, { WritingCanvasHandle } from "@/components/WritingCanvas";
import StreamingReply from "@/components/StreamingReply";
import MagicalParticles from "@/components/MagicalParticles";

type RiddleState = "hidden" | "writing" | "visible" | "fading";

export default function DiaryPage() {
  const [isProcessing, setIsProcessing] = useState(false);
  const [streamedText, setStreamedText] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);

  // Tom Riddle text state
  const [riddleState, setRiddleState] = useState<RiddleState>("hidden");
  const [currentResponse, setCurrentResponse] = useState("");

  const canvasRef = useRef<WritingCanvasHandle>(null);
  const fadeTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  // Sync streamed text with the response display
  useEffect(() => {
    if (streamedText) {
      setCurrentResponse(streamedText);
      setRiddleState("writing");
    }
  }, [streamedText]);

  // Handle auto-fading after Riddle finishes writing
  useEffect(() => {
    if (riddleState === "visible") {
      // Keep Riddle's response on screen for 5 seconds before fading
      fadeTimeoutRef.current = setTimeout(() => {
        setRiddleState("fading");
      }, 5000);
    } else if (riddleState === "fading") {
      // Clear text after the 2s fade-out transition completes
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
  const sendToDiary = useCallback(async (imageData: string) => {
    setIsProcessing(true);
    setStreamedText("");
    setIsStreaming(true);
    setRiddleState("hidden");
    setCurrentResponse("");

    try {
      const res = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ imageData }),
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
            continue;
          }

          try {
            const parsed = JSON.parse(data);
            if (parsed.error) {
              throw new Error(parsed.error);
            }
            if (parsed.token) {
              accumulated += parsed.token;
              setStreamedText(accumulated);
            }
          } catch {
            // Skip parsing errors
          }
        }
      }
    } catch (err) {
      console.error("Diary error:", err);
      setIsStreaming(false);
      setCurrentResponse("...");
      setRiddleState("visible");
      setStreamedText("");
    } finally {
      setIsProcessing(false);
    }
  }, []);

  /** Handle canvas handwriting submission */
  const handleCanvasSubmit = useCallback(
    async (imageData: string) => {
      if (isProcessing) return;

      // Clear any pending Riddle fade timer
      if (fadeTimeoutRef.current) {
        clearTimeout(fadeTimeoutRef.current);
      }
      setRiddleState("hidden");

      // Trigger user drawing fade-out
      await canvasRef.current?.fadeOut();

      // Send captured drawing base64 data to Gemini
      await sendToDiary(imageData);
    },
    [isProcessing, sendToDiary]
  );

  return (
    <div className="relative h-screen w-screen bg-[#ecebe6] overflow-hidden select-none touch-none">
      {/* Subtle magical ambient particles */}
      <MagicalParticles count={8} />

      {/* Realistic paper overlay texture & subtle vignette shadow */}
      <div 
        className="absolute inset-0 pointer-events-none z-10" 
        style={{
          boxShadow: 'inset 0 0 100px rgba(0, 0, 0, 0.04)',
          background: 'radial-gradient(circle, transparent 60%, rgba(0,0,0,0.02) 100%)',
        }}
      />

      {/* The drawing canvas (covers the entire screen) */}
      <div className="absolute inset-0 z-20 pointer-events-auto">
        <WritingCanvas
          ref={canvasRef}
          onSubmit={handleCanvasSubmit}
          disabled={isProcessing}
          color="#222228" // Dark grey slate ink
          thickness={2.5}
          tool="pen"
          autoSubmitDelay={1600} // Trigger fade/send after 1.6s of pen lift
        />
      </div>

      {/* Riddle's Ghostly Response Layer */}
      {currentResponse && (
        <div
          className={`absolute inset-0 flex items-center justify-center pointer-events-none z-30 px-10 transition-all duration-[2000ms] ${
            riddleState === "fading"
              ? "opacity-0 blur-[2px]"
              : "opacity-100 blur-0"
          }`}
        >
          <div className="max-w-xl text-center">
            <StreamingReply
              text={currentResponse}
              isStreaming={isStreaming}
            />
          </div>
        </div>
      )}
    </div>
  );
}
