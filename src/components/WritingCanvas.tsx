'use client';

import React, {
  useRef,
  useState,
  useEffect,
  useCallback,
  useImperativeHandle,
  forwardRef,
} from 'react';

export interface Point {
  x: number;
  y: number;
  pressure: number;
}

export interface Stroke {
  id: string;
  points: Point[];
  color: string;
  width: number;
}

interface WritingCanvasProps {
  onSubmit: (imageData: string) => void;
  disabled?: boolean;
  color?: string;
  thickness?: number;
  tool?: 'pen' | 'eraser';
  onStrokeChange?: (hasContent: boolean) => void;
  autoSubmitDelay?: number;
}

export interface WritingCanvasHandle {
  fadeOut: () => Promise<void>;
  clear: () => void;
  undo: () => void;
  redo: () => void;
  canUndo: boolean;
  canRedo: boolean;
}

const WritingCanvas = forwardRef<WritingCanvasHandle, WritingCanvasProps>(
  (
    {
      onSubmit,
      disabled = false,
      color = '#1a1a2e',
      thickness = 3,
      tool = 'pen',
      onStrokeChange,
      autoSubmitDelay = 1500,
    },
    ref
  ) => {
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const containerRef = useRef<HTMLDivElement>(null);
    
    const isDrawingRef = useRef(false);
    const currentStrokeRef = useRef<Stroke | null>(null);
    
    // React state for undo/redo UI tracking
    const [strokes, setStrokes] = useState<Stroke[]>([]);
    const [undoStack, setUndoStack] = useState<Stroke[][]>([]);
    const [redoStack, setRedoStack] = useState<Stroke[][]>([]);
    const [globalOpacity, setGlobalOpacity] = useState(1);

    // Keep a mutable ref of the strokes list to avoid stale closures in event handlers
    const strokesRef = useRef<Stroke[]>([]);
    useEffect(() => {
      strokesRef.current = strokes;
      onStrokeChange?.(strokes.length > 0);
    }, [strokes, onStrokeChange]);

    const submitTimerRef = useRef<NodeJS.Timeout | null>(null);

    // Context helper
    const getCtx = useCallback(() => {
      const canvas = canvasRef.current;
      if (!canvas) return null;
      return canvas.getContext('2d');
    }, []);

    // Vector drawing function
    const drawStrokes = useCallback(
      (strokesList: Stroke[], opacity = 1) => {
        const canvas = canvasRef.current;
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        if (!ctx) return;

        const dpr = window.devicePixelRatio || 1;
        ctx.setTransform(1, 0, 0, 1, 0, 0);
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';

        strokesList.forEach((stroke) => {
          if (stroke.points.length === 0) return;

          ctx.beginPath();
          ctx.strokeStyle = stroke.color;
          ctx.globalAlpha = opacity;

          if (stroke.points.length === 1) {
            const p = stroke.points[0];
            ctx.fillStyle = stroke.color;
            ctx.beginPath();
            ctx.arc(p.x, p.y, (stroke.width * p.pressure) / 2, 0, Math.PI * 2);
            ctx.fill();
          } else {
            ctx.moveTo(stroke.points[0].x, stroke.points[0].y);
            
            for (let i = 1; i < stroke.points.length - 1; i++) {
              const xc = (stroke.points[i].x + stroke.points[i + 1].x) / 2;
              const yc = (stroke.points[i].y + stroke.points[i + 1].y) / 2;
              ctx.lineWidth = stroke.width * (stroke.points[i].pressure || 0.6);
              ctx.quadraticCurveTo(stroke.points[i].x, stroke.points[i].y, xc, yc);
            }
            
            const lastIdx = stroke.points.length - 1;
            ctx.lineWidth = stroke.width * (stroke.points[lastIdx].pressure || 0.6);
            ctx.lineTo(stroke.points[lastIdx].x, stroke.points[lastIdx].y);
            ctx.stroke();
          }
        });

        ctx.globalAlpha = 1;
      },
      []
    );

    // Redraw canvas when state changes
    useEffect(() => {
      drawStrokes(strokes, globalOpacity);
    }, [strokes, globalOpacity, drawStrokes]);

    // Handle container resize (retina-aware)
    const resizeCanvas = useCallback(() => {
      const canvas = canvasRef.current;
      const container = containerRef.current;
      if (!canvas || !container) return;

      const rect = container.getBoundingClientRect();
      const dpr = window.devicePixelRatio || 1;

      canvas.width = rect.width * dpr;
      canvas.height = rect.height * dpr;

      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      }
      
      drawStrokes(strokesRef.current, globalOpacity);
    }, [globalOpacity, drawStrokes]);

    // Resize observer
    useEffect(() => {
      const container = containerRef.current;
      if (!container) return;

      resizeCanvas();
      const observer = new ResizeObserver(() => resizeCanvas());
      observer.observe(container);

      return () => observer.disconnect();
    }, [resizeCanvas]);

    // Submission timers
    const clearSubmitTimer = useCallback(() => {
      if (submitTimerRef.current) {
        clearTimeout(submitTimerRef.current);
        submitTimerRef.current = null;
      }
    }, []);

    const triggerSubmit = useCallback(() => {
      const canvas = canvasRef.current;
      if (!canvas || strokesRef.current.length === 0 || disabled) return;
      
      const imageData = canvas.toDataURL('image/png');
      onSubmit(imageData);
    }, [disabled, onSubmit]);

    const resetSubmitTimer = useCallback(() => {
      clearSubmitTimer();
      if (disabled || strokesRef.current.length === 0) return;

      submitTimerRef.current = setTimeout(() => {
        triggerSubmit();
      }, autoSubmitDelay);
    }, [clearSubmitTimer, disabled, autoSubmitDelay, triggerSubmit]);

    const getPointerPos = useCallback((e: React.PointerEvent<HTMLCanvasElement>) => {
      const canvas = canvasRef.current;
      if (!canvas) return { x: 0, y: 0 };
      const rect = canvas.getBoundingClientRect();
      return {
        x: e.clientX - rect.left,
        y: e.clientY - rect.top,
      };
    }, []);

    // Vector Eraser Hit Testing
    const eraseAtPoint = useCallback((point: { x: number; y: number }) => {
      const eraseRadius = 18; // px threshold
      let hit = false;

      const remainingStrokes = strokesRef.current.filter((stroke) => {
        const isNear = stroke.points.some((p) => {
          const dx = p.x - point.x;
          const dy = p.y - point.y;
          return Math.sqrt(dx * dx + dy * dy) < eraseRadius;
        });

        if (isNear) hit = true;
        return !isNear;
      });

      if (hit) {
        setUndoStack((prev) => [...prev, strokesRef.current]);
        setStrokes(remainingStrokes);
      }
    }, []);

    // Pointer event handlers
    const handlePointerDown = useCallback(
      (e: React.PointerEvent<HTMLCanvasElement>) => {
        if (disabled) return;
        const canvas = canvasRef.current;
        if (canvas) {
          canvas.setPointerCapture(e.pointerId);
        }

        clearSubmitTimer();
        isDrawingRef.current = true;
        const pos = getPointerPos(e);
        const pressure = e.pressure !== undefined && e.pressure > 0 ? e.pressure : 0.6;

        if (tool === 'pen') {
          const newStroke: Stroke = {
            id: crypto.randomUUID(),
            points: [{ x: pos.x, y: pos.y, pressure }],
            color,
            width: thickness,
          };
          currentStrokeRef.current = newStroke;

          // Draw directly to the screen immediately for zero latency
          const ctx = getCtx();
          if (ctx) {
            ctx.lineCap = 'round';
            ctx.lineJoin = 'round';
            ctx.strokeStyle = color;
            ctx.lineWidth = thickness * pressure;
            ctx.beginPath();
            ctx.arc(pos.x, pos.y, (thickness * pressure) / 2, 0, Math.PI * 2);
            ctx.fillStyle = color;
            ctx.fill();
          }
        } else if (tool === 'eraser') {
          eraseAtPoint(pos);
        }
      },
      [disabled, getPointerPos, tool, color, thickness, getCtx, eraseAtPoint, clearSubmitTimer]
    );

    const handlePointerMove = useCallback(
      (e: React.PointerEvent<HTMLCanvasElement>) => {
        if (!isDrawingRef.current || disabled) return;

        const pos = getPointerPos(e);
        const pressure = e.pressure !== undefined && e.pressure > 0 ? e.pressure : 0.6;

        if (tool === 'pen' && currentStrokeRef.current) {
          const stroke = currentStrokeRef.current;
          const lastPoint = stroke.points[stroke.points.length - 1];
          const newPoint = { x: pos.x, y: pos.y, pressure };
          stroke.points.push(newPoint);

          // Draw segment directly onto canvas context
          const ctx = getCtx();
          if (ctx) {
            ctx.lineCap = 'round';
            ctx.lineJoin = 'round';
            ctx.strokeStyle = color;
            ctx.lineWidth = thickness * pressure;

            ctx.beginPath();
            ctx.moveTo(lastPoint.x, lastPoint.y);
            ctx.lineTo(pos.x, pos.y);
            ctx.stroke();
          }
        } else if (tool === 'eraser') {
          eraseAtPoint(pos);
        }
      },
      [disabled, getPointerPos, tool, color, thickness, getCtx, eraseAtPoint]
    );

    const handlePointerUp = useCallback(
      (e: React.PointerEvent<HTMLCanvasElement>) => {
        if (!isDrawingRef.current) return;
        isDrawingRef.current = false;

        const canvas = canvasRef.current;
        if (canvas) {
          canvas.releasePointerCapture(e.pointerId);
        }

        if (tool === 'pen' && currentStrokeRef.current) {
          const completedStroke = currentStrokeRef.current;
          currentStrokeRef.current = null;

          // Push history and append new completed stroke
          setUndoStack((prev) => [...prev, strokesRef.current]);
          setRedoStack([]);
          setStrokes((prev) => [...prev, completedStroke]);
        }

        resetSubmitTimer();
      },
      [tool, resetSubmitTimer]
    );

    const undo = useCallback(() => {
      if (undoStack.length === 0) return;
      clearSubmitTimer();

      const previous = undoStack[undoStack.length - 1];
      setUndoStack((prev) => prev.slice(0, -1));
      setRedoStack((prev) => [...prev, strokesRef.current]);
      setStrokes(previous);

      setTimeout(() => resetSubmitTimer(), 50);
    }, [undoStack, resetSubmitTimer, clearSubmitTimer]);

    const redo = useCallback(() => {
      if (redoStack.length === 0) return;
      clearSubmitTimer();

      const next = redoStack[redoStack.length - 1];
      setRedoStack((prev) => prev.slice(0, -1));
      setUndoStack((prev) => [...prev, strokesRef.current]);
      setStrokes(next);

      setTimeout(() => resetSubmitTimer(), 50);
    }, [redoStack, resetSubmitTimer, clearSubmitTimer]);

    const clear = useCallback(() => {
      clearSubmitTimer();
      setUndoStack([]);
      setRedoStack([]);
      setStrokes([]);
      setGlobalOpacity(1);
    }, [clearSubmitTimer]);

    const fadeOut = useCallback((): Promise<void> => {
      return new Promise((resolve) => {
        clearSubmitTimer();
        const duration = 1200; // ms
        const startTime = performance.now();

        const animate = (currentTime: number) => {
          const elapsed = currentTime - startTime;
          const progress = Math.min(elapsed / duration, 1);
          const opacity = 1 - progress;
          setGlobalOpacity(opacity);

          if (progress < 1) {
            requestAnimationFrame(animate);
          } else {
            clear();
            resolve();
          }
        };

        requestAnimationFrame(animate);
      });
    }, [clear, clearSubmitTimer]);

    useImperativeHandle(
      ref,
      () => ({
        fadeOut,
        clear,
        undo,
        redo,
        canUndo: undoStack.length > 0,
        canRedo: redoStack.length > 0,
      }),
      [fadeOut, clear, undo, redo, undoStack.length, redoStack.length]
    );

    return (
      <div ref={containerRef} className="relative w-full h-full">
        <canvas
          ref={canvasRef}
          className="absolute inset-0 w-full h-full drawing-canvas select-none"
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          onPointerLeave={handlePointerUp}
        />
      </div>
    );
  }
);

WritingCanvas.displayName = 'WritingCanvas';

export default WritingCanvas;
