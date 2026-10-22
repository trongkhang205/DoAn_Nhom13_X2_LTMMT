"use client";

import React from "react";
import { motion } from "framer-motion";
import { ArrowRight, ShieldCheck, Zap, Sparkles } from "lucide-react";

export interface AnimatedHeroTextProps {
  onStartClick?: () => void;
  className?: string;
}

export default function AnimatedHeroText({ onStartClick, className = "" }: AnimatedHeroTextProps) {
  const titleText = "HỆ THỐNG ĐIỀU KHIỂN MÁY TÍNH TỪ XA";

  const containerVariants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: {
        staggerChildren: 0.035,
        delayChildren: 0.1,
      },
    },
  };

  const letterVariants = {
    hidden: { opacity: 0, y: 15 },
    visible: {
      opacity: 1,
      y: 0,
      transition: { duration: 0.45, ease: [0.2, 0.65, 0.3, 0.9] },
    },
  };

  return (
    <div className={`flex flex-col items-center lg:items-start text-center lg:text-left z-10 ${className}`}>
      {/* BADGE PULSE */}
      <motion.div
        initial={{ opacity: 0, scale: 0.85, y: -10 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        transition={{ duration: 0.5, ease: "easeOut" }}
        className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-pink-500/10 border border-pink-500/25 text-pink-400 text-xs font-semibold tracking-wide mb-5 backdrop-blur-md shadow-[0_0_15px_rgba(236,72,153,0.15)]"
      >
        <span className="relative flex h-2 w-2">
          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-pink-400 opacity-75"></span>
          <span className="relative inline-flex rounded-full h-2 w-2 bg-pink-500"></span>
        </span>
        <Sparkles className="w-3.5 h-3.5 text-pink-400" />
        <span>NEXT-GEN WEB REMOTE DESKTOP 2.0</span>
      </motion.div>

      {/* TYPING / STAGGERED HERO TITLE */}
      <motion.h1
        variants={containerVariants}
        initial="hidden"
        animate="visible"
        className="text-3xl sm:text-4xl md:text-5xl lg:text-6xl font-extrabold tracking-tight text-slate-100 max-w-2xl leading-[1.15]"
      >
        {titleText.split(" ").map((word, wordIndex) => (
          <span key={wordIndex} className="inline-block mr-2.5">
            {word.split("").map((char, charIndex) => (
              <motion.span
                key={charIndex}
                variants={letterVariants}
                className={
                  word === "TỪ" || word === "XA" || word === "ĐIỀU" || word === "KHIỂN"
                    ? "bg-gradient-to-r from-rose-400 via-pink-400 to-sky-400 bg-clip-text text-transparent"
                    : ""
                }
              >
                {char}
              </motion.span>
            ))}
          </span>
        ))}
      </motion.h1>

      {/* SUBTITLE */}
      <motion.p
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, delay: 0.6 }}
        className="mt-5 text-base sm:text-lg text-slate-300/80 max-w-xl leading-relaxed"
      >
        Kiến trúc Web thuần Java Zero-Dependency. Điều khiển mượt mà đến{" "}
        <span className="text-pink-400 font-semibold">60 FPS HD</span>, truyền tải tệp tin{" "}
        <span className="text-sky-400 font-semibold">128KB Multi-file</span> và kết nối xuyên Internet 4G chỉ với 1 click.
      </motion.p>

      {/* HIGHLIGHT PILLS */}
      <motion.div
        initial={{ opacity: 0, y: 15 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, delay: 0.8 }}
        className="flex flex-wrap justify-center lg:justify-start gap-3 mt-6 text-xs text-slate-300"
      >
        <span className="flex items-center gap-1.5 px-3 py-1 rounded-lg bg-slate-800/60 border border-slate-700/60 backdrop-blur-sm">
          <Zap className="w-3.5 h-3.5 text-yellow-400" />
          <span>Delta Diffing &lt; 0.8ms</span>
        </span>
        <span className="flex items-center gap-1.5 px-3 py-1 rounded-lg bg-slate-800/60 border border-slate-700/60 backdrop-blur-sm">
          <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
          <span>RFC 6455 Native WebSocket</span>
        </span>
      </motion.div>

      {/* CTA ACTIONS */}
      <motion.div
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.5, delay: 0.95 }}
        className="mt-8 flex flex-col sm:flex-row items-center gap-4 w-full sm:w-auto"
      >
        {/* Animated Glow CTA Button */}
        <button
          onClick={onStartClick}
          className="group relative inline-flex items-center justify-center gap-3 px-8 py-4 rounded-xl text-white font-bold text-sm tracking-wide overflow-hidden w-full sm:w-auto shadow-[0_0_35px_rgba(244,63,94,0.35)] transition-all duration-300 hover:shadow-[0_0_50px_rgba(236,72,153,0.55)] hover:-translate-y-0.5 active:translate-y-0"
        >
          {/* Animated Gradient Background */}
          <span className="absolute inset-0 bg-gradient-to-r from-rose-500 via-pink-500 to-indigo-600 bg-[length:200%_200%] animate-[gradient_4s_ease_infinite]" />
          
          {/* Shimmer Light Sweep */}
          <span className="absolute top-0 -left-full w-1/2 h-full bg-white/20 skew-x-12 group-hover:left-[150%] transition-all duration-700 ease-out" />

          <span className="relative z-10 flex items-center gap-2">
            <span>BẮT ĐẦU NGAY</span>
            <ArrowRight className="w-4 h-4 transition-transform group-hover:translate-x-1.5 duration-200" />
          </span>
        </button>

        {/* Secondary Button */}
        <a
          href="#featuresSection"
          className="inline-flex items-center justify-center px-6 py-4 rounded-xl text-slate-300 hover:text-white bg-slate-800/40 hover:bg-slate-800/70 border border-slate-700/50 backdrop-blur-md text-sm font-medium transition-all duration-200 w-full sm:w-auto"
        >
          Xem 3 Chức Năng
        </a>
      </motion.div>
    </div>
  );
}
