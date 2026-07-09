'use client';

import React, { useState, useEffect } from 'react';

export default function LandingPage() {
  const [visits, setVisits] = useState<number | null>(null);

  useEffect(() => {
    fetch('/api/visits')
      .then((res) => res.json())
      .then((data) => {
        if (typeof data.visits === 'number') {
          setVisits(data.visits);
        }
      })
      .catch((err) => console.error('Failed to load visit count:', err));
  }, []);

  return (
    <div className="min-h-screen bg-[#FDFDFB] text-[#222228] selection:bg-amber-100 selection:text-amber-900 relative overflow-x-hidden overflow-y-auto font-sans flex flex-col justify-between">
      
      {/* Import Google Fonts directly */}
      <style dangerouslySetInnerHTML={{ __html: `
        @import url('https://fonts.googleapis.com/css2?family=EB+Garamond:ital,wght@0,400..800;1,400..800&family=Inter:wght@300;400;500;600;700&family=Caveat:wght@400..700&display=swap');
        
        .font-serif {
          font-family: 'EB Garamond', Georgia, serif;
        }
        .font-sans {
          font-family: 'Inter', sans-serif;
        }
        .font-cursive {
          font-family: 'Caveat', cursive;
        }

        /* Ruled lines pattern for the notebook look */
        .ruled-bg {
          background-image: linear-gradient(#F3F1EB 1px, transparent 1px);
          background-size: 100% 32px;
        }

        /* Animations for the interactive diary mockup */
        @keyframes writePrompt {
          0%, 10% { opacity: 0; filter: blur(2px); transform: translateY(2px); }
          15%, 40% { opacity: 0.85; filter: blur(0); transform: translateY(0); }
          50%, 100% { opacity: 0; filter: blur(4px); }
        }

        @keyframes writeReply {
          0%, 55% { opacity: 0; filter: blur(3px); transform: scale(0.98); }
          65%, 90% { opacity: 1; filter: blur(0); transform: scale(1); }
          95%, 100% { opacity: 0; filter: blur(4px); }
        }

        @keyframes sparkleScale {
          0%, 100% { transform: scale(1) rotate(0deg); }
          50% { transform: scale(1.2) rotate(15deg); }
        }

        .animate-prompt {
          animation: writePrompt 10s infinite ease-in-out;
        }

        .animate-reply {
          animation: writeReply 10s infinite ease-in-out;
        }

        .animate-sparkle {
          animation: sparkleScale 1.5s infinite ease-in-out;
        }
      `}} />

      {/* Subtle paper grain texture overlay */}
      <div className="absolute inset-0 opacity-[0.015] pointer-events-none bg-[radial-gradient(#000_1px,transparent_1px)] [background-size:16px_16px] z-40" />

      {/* Header */}
      <header className="max-w-6xl w-full mx-auto px-8 py-8 flex justify-between items-center relative z-20">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-[#1C1C21] flex items-center justify-center shadow-md">
            <svg className="w-4 h-4 text-white" fill="none" viewBox="0 0 24 24" strokeWidth={2.5} stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 6.042A8.967 8.967 0 0 0 6 3.75c-1.052 0-2.062.18-3 .512v14.25A8.987 8.987 0 0 1 6 18c2.305 0 4.408.867 6 2.292m0-14.25a8.966 8.966 0 0 1 6-2.292c1.052 0 2.062.18 3 .512v14.25A8.987 8.987 0 0 0 18 18a8.967 8.967 0 0 0-6 2.292m0-14.25v14.25" />
            </svg>
          </div>
          <span className="font-semibold text-[#1C1C21] tracking-tight text-base font-sans">Notewise Riddle</span>
        </div>

        <div className="flex items-center gap-3">
          <a
            href="https://github.com/Adityazzzzz/Riddle"
            target="_blank"
            rel="noopener noreferrer"
            className="text-[#222228] hover:text-[#1C1C21] hover:bg-stone-100 transition-all flex items-center gap-1.5 text-[10px] font-bold tracking-widest uppercase border border-stone-300 bg-white px-3 py-1.5 rounded-full shadow-sm"
          >
            <svg className="w-3.5 h-3.5 fill-current" viewBox="0 0 24 24">
              <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"/>
            </svg>
            <span>Star on GitHub</span>
          </a>

          {visits !== null && (
            <span className="text-[10px] font-semibold text-stone-500 tracking-wider bg-stone-100 px-3 py-1.5 rounded-full flex items-center gap-1.5 border border-stone-200/60 uppercase">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
              {visits.toLocaleString()} visits
            </span>
          )}
          <span className="text-[10px] font-bold tracking-widest text-[#1C1C21] border border-[#1C1C21]/20 px-3 py-1.5 rounded-full uppercase">
            v1.0.4
          </span>
        </div>
      </header>

      {/* Two-Column Editorial Layout (Single Screen Focus) */}
      <main className="max-w-6xl w-full mx-auto px-8 flex-1 flex items-center justify-center py-6 relative z-10">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-16 items-center w-full">
          
          {/* Left Column: Premium copy & Action */}
          <div className="lg:col-span-6 flex flex-col items-start text-left">
            <span className="text-xs font-bold uppercase tracking-[0.2em] text-[#1C1C21]/60 mb-4 block">
              A Magical zero-ui experiment
            </span>
            
            <h1 className="text-5xl md:text-6.5xl font-serif text-[#1C1C21] tracking-tight leading-[1.03] font-light">
              Write on the page. <br />
              Watch the ink <br />
              <span className="italic font-normal text-amber-700 font-serif">dissolve into paper.</span>
            </h1>

            <p className="mt-6 text-stone-600 text-base md:text-lg leading-relaxed font-light max-w-lg">
              No keyboards, no screen glow, no modern chat windows. Just a native Android tablet app that turns a blank sheet of paper into a direct portal to Hogwarts.
            </p>

            {/* Premium Download Action Card */}
            <div className="mt-10 flex flex-col sm:flex-row items-start sm:items-center gap-6 w-full">
              <a
                href="/riddle-diary.apk"
                download="riddle-diary.apk"
                className="px-8 py-4.5 rounded-xl bg-[#1C1C21] hover:bg-[#2d2d35] text-white font-medium shadow-xl shadow-[#1c1c21]/15 transition-all duration-300 active:scale-98 flex items-center gap-3.5 text-base border border-stone-800"
              >
                <svg className="w-5 h-5 text-stone-300" fill="none" viewBox="0 0 24 24" strokeWidth={2.5} stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75V16.5M16.5 12 12 16.5m0 0L7.5 12m4.5 4.5V3" />
                </svg>
                Download for Android
              </a>
              
              <div className="text-left">
                <span className="block text-xs font-bold text-[#1c1c21]/90 uppercase tracking-widest">riddle-diary.apk</span>
                <span className="block text-xs text-stone-400 font-medium mt-0.5">Size: 11.4 MB • Android 7.0+</span>
              </div>
            </div>

            {/* Quick installation specs badge */}
            <div className="mt-12 pt-8 border-t border-stone-200/60 w-full max-w-md flex justify-between text-xs text-stone-500 font-medium tracking-wide uppercase">
              <span>✓ Stylus Pressure</span>
              <span>✓ 5-Finger Key Config</span>
              <span>✓ 5 Personas</span>
            </div>
          </div>

          {/* Right Column: Premium Animated Device Mockup */}
          <div className="lg:col-span-6 flex flex-col items-center lg:items-end justify-center">
            <div className="w-full max-w-[460px] bg-[#222228] rounded-[36px] p-4.5 shadow-[0_25px_60px_-15px_rgba(0,0,0,0.22)] border border-stone-800/80 relative">
              
              {/* Glossy bezel highlights */}
              <div className="absolute inset-0 rounded-[36px] border border-white/5 pointer-events-none" />
              <div className="absolute top-0 left-1/2 -translate-x-1/2 w-20 h-4 bg-stone-900 rounded-b-xl z-20 flex items-center justify-center">
                <div className="w-2 h-2 rounded-full bg-stone-800" />
              </div>

              {/* Tablet screen container (Magical paper diary view) */}
              <div className="aspect-[3/4] w-full rounded-[24px] bg-[#FAF8F5] relative overflow-hidden border border-stone-900/60 p-8 shadow-inner flex flex-col justify-between select-none">
                
                {/* Ruled lines overlay */}
                <div className="absolute inset-x-8 top-20 bottom-12 ruled-bg pointer-events-none" />

                {/* Top paper header */}
                <div className="relative z-10 flex justify-between items-center">
                  <span className="text-[10px] font-semibold text-stone-400 uppercase tracking-widest font-sans">JULY 9, 2026</span>
                  <div className="flex items-center gap-1.5 bg-[#FAF8F5] border border-amber-600/20 px-2.5 py-0.5 rounded-full">
                    <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse" />
                    <span className="text-[8px] font-bold text-amber-700 uppercase tracking-widest font-sans font-medium">Tom Riddle</span>
                  </div>
                </div>

                {/* Main Content Area (Animated writing simulation) */}
                <div className="relative z-10 flex-1 flex flex-col justify-center items-center py-8">
                  
                  {/* 1. User handwritten prompt (Fades away like ink absorbing) */}
                  <div className="absolute text-center animate-prompt">
                    <p className="font-cursive text-3.5xl text-stone-850 leading-relaxed font-normal">
                      Who are you?
                    </p>
                    <span className="text-[9px] text-stone-400 font-sans block mt-1 tracking-wider uppercase">
                      (ink fading...)
                    </span>
                  </div>

                  {/* 2. Riddle's responsive reply (Materializes from paper) */}
                  <div className="absolute text-center animate-reply px-4">
                    <p className="font-cursive text-3.5xl text-amber-950 font-normal leading-relaxed">
                      I am Tom Riddle. <br />
                      How did you find my diary?
                    </p>
                  </div>

                </div>

                {/* Bottom paper notes */}
                <div className="relative z-10 flex justify-between items-center text-[9px] text-stone-400 font-medium tracking-wide uppercase font-sans">
                  <span>✎ Scribble with pen...</span>
                  <span>Response in &lt; 2s</span>
                </div>
              </div>
            </div>

            {/* Interactive Creator Signature (Matches user screenshots exactly) */}
            <a
              href="https://github.com/Adityazzzzz"
              target="_blank"
              rel="noopener noreferrer"
              className="group flex items-center gap-4 cursor-pointer select-none no-underline justify-end mt-8 relative z-20 pr-4 hover:scale-103 transition-transform duration-300 origin-right"
            >
              <div className="w-12 h-[1px] bg-stone-300 group-hover:w-16 group-hover:bg-[#1C1C21] transition-all duration-500" />
              <span className="font-cursive text-2xl text-stone-500 group-hover:text-stone-800 transition-colors">
                Developed by <span className="font-bold text-[#1C1C21] font-cursive">Aditya.</span>
              </span>
            </a>
          </div>

        </div>
      </main>

      {/* Footer (Minimal copy inside main viewport wrapper) */}
      <footer className="w-full text-center py-8 text-[10px] text-stone-400 font-medium uppercase tracking-widest relative z-20 border-t border-stone-200/40">
        <p>© 2026 Notewise Riddle Diary Project • designed with love by adityazzzz</p>
      </footer>
    </div>
  );
}
