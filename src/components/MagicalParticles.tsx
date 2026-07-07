'use client';

import React, { useMemo, useState, useEffect } from 'react';

interface MagicalParticlesProps {
  count?: number;
}

interface ParticleConfig {
  id: number;
  x: number;        // % position
  y: number;        // % position
  size: number;     // px
  duration: number;  // seconds
  delay: number;    // seconds
  opacity: number;
  drift: number;    // px vertical drift
}

const MagicalParticles: React.FC<MagicalParticlesProps> = ({ count = 30 }) => {
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);
  // Generate particle configs once
  const particles = useMemo<ParticleConfig[]>(() => {
    return Array.from({ length: count }, (_, i) => ({
      id: i,
      x: Math.random() * 100,
      y: Math.random() * 100,
      size: 2 + Math.random() * 2,               // 2-4px
      duration: 6 + Math.random() * 6,            // 6-12s
      delay: Math.random() * -12,                 // stagger start (negative = already mid-animation)
      opacity: 0.08 + Math.random() * 0.18,       // 0.08-0.26
      drift: 12 + Math.random() * 16,             // 12-28px
    }));
  }, [count]);

  if (!mounted) return null;

  return (
    <div
      className="fixed inset-0 pointer-events-none z-0 overflow-hidden"
      aria-hidden="true"
    >
      <style>{`
        @keyframes particleFloat {
          0%, 100% {
            transform: translateY(0) translateX(0);
          }
          25% {
            transform: translateY(calc(var(--drift) * -1)) translateX(3px);
          }
          50% {
            transform: translateY(0) translateX(-2px);
          }
          75% {
            transform: translateY(var(--drift)) translateX(1px);
          }
        }
      `}</style>

      {particles.map((p) => (
        <div
          key={p.id}
          className="absolute rounded-full bg-amber-300"
          style={{
            left: `${p.x}%`,
            top: `${p.y}%`,
            width: `${p.size}px`,
            height: `${p.size}px`,
            opacity: p.opacity,
            boxShadow: `0 0 ${p.size * 2}px rgba(200, 170, 100, 0.25)`,
            animation: `particleFloat ${p.duration}s ease-in-out infinite`,
            animationDelay: `${p.delay}s`,
            ['--drift' as string]: `${p.drift}px`,
          }}
        />
      ))}
    </div>
  );
};

export default MagicalParticles;
