"use client";

import React, { useState, useEffect, Suspense, lazy } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Sun, Moon, Laptop, Globe, Shield, Terminal } from "lucide-react";
import AnimatedHeroText from "./AnimatedHeroText";
import FeatureCards from "./FeatureCards";

// Lazy-load 3D Scene to keep bundle lean and avoid SSR issues
const Hero3DScene = lazy(() => import("./Hero3DScene"));

export interface HomePageProps {
  onNavigate?: (view: "server" | "client" | "test") => void;
}

export default function HomePage({ onNavigate }: HomePageProps) {
  const [theme, setTheme] = useState<"dark" | "light">("dark");
  const [isMobile, setIsMobile] = useState<boolean>(false);

  // Check mobile screen & saved theme
  useEffect(() => {
    const checkMobile = () => {
      setIsMobile(window.innerWidth < 768);
    };
    checkMobile();
    window.addEventListener("resize", checkMobile);

    const savedTheme = (localStorage.getItem("remote_app_theme") as "dark" | "light") || "dark";
    setTheme(savedTheme);
    document.documentElement.setAttribute("data-theme", savedTheme);

    return () => window.removeEventListener("resize", checkMobile);
  }, []);

  const toggleTheme = () => {
    const nextTheme = theme === "dark" ? "light" : "dark";
    setTheme(nextTheme);
    localStorage.setItem("remote_app_theme", nextTheme);
    document.documentElement.setAttribute("data-theme", nextTheme);
  };

  const handleScrollToCards = () => {
    const el = document.getElementById("featuresSection");
    if (el) {
      el.scrollIntoView({ behavior: "smooth" });
    }
  };

  return (
    <div
      className={`min-h-screen relative overflow-hidden transition-colors duration-300 font-sans ${
        theme === "dark" ? "bg-[#0c0912] text-slate-100" : "bg-[#f8fafc] text-slate-900"
      }`}
    >
      {/* 1. CYBERPUNK / VERCEL INFINITE GRID BACKGROUND */}
      <div
        className="pointer-events-none absolute inset-0 z-0 opacity-[0.22] dark:opacity-[0.25]"
        style={{
          backgroundImage:
            theme === "dark"
              ? "linear-gradient(to right, rgba(244,63,94,0.18) 1px, transparent 1px), linear-gradient(to bottom, rgba(244,63,94,0.18) 1px, transparent 1px)"
              : "linear-gradient(to right, rgba(244,63,94,0.12) 1px, transparent 1px), linear-gradient(to bottom, rgba(244,63,94,0.12) 1px, transparent 1px)",
          backgroundSize: "48px 48px",
          maskImage: "radial-gradient(ellipse 65% 55% at 50% 25%, #000 60%, transparent 100%)",
          WebkitMaskImage: "radial-gradient(ellipse 65% 55% at 50% 25%, #000 60%, transparent 100%)",
        }}
      />

      {/* 2. GLOWING AMBIENT ORBS (BLURRED BACKGROUND ACCENTS) */}
      <div className="pointer-events-none absolute -top-40 -left-40 w-96 h-96 rounded-full bg-rose-600/20 blur-[130px]" />
      <div className="pointer-events-none absolute top-1/4 -right-40 w-[450px] h-[450px] rounded-full bg-pink-500/20 blur-[150px]" />
      <div className="pointer-events-none absolute bottom-10 left-1/3 w-[500px] h-[500px] rounded-full bg-sky-500/15 blur-[160px]" />

      {/* 3. TOP NAVIGATION BAR */}
      <header className="relative z-20 w-full border-b border-slate-800/40 dark:border-slate-800/60 backdrop-blur-md">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-18 flex items-center justify-between py-4">
          {/* LOGO */}
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-rose-500 to-pink-500 flex items-center justify-center shadow-[0_0_20px_rgba(244,63,94,0.45)]">
              <Laptop className="w-5 h-5 text-white" />
            </div>
            <div>
              <span className="font-extrabold text-base tracking-wide bg-gradient-to-r from-rose-400 via-pink-400 to-sky-400 bg-clip-text text-transparent">
                REMOTE DESKTOP
              </span>
              <span className="text-[10px] block font-mono text-slate-400">Đồ Án Nhóm 13 (X2)</span>
            </div>
          </div>

          {/* RIGHT ACTIONS: STATUS & THEME TOGGLE */}
          <div className="flex items-center gap-4">
            <div className="hidden sm:flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-medium">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
              <span>Máy Chủ Sẵn Sàng (Port 8080)</span>
            </div>

            {/* THEME TOGGLE BUTTON */}
            <button
              onClick={toggleTheme}
              aria-label="Chuyển chế độ Sáng / Tối"
              className="p-2.5 rounded-xl bg-slate-800/50 hover:bg-slate-700/60 border border-slate-700/60 transition-all duration-200 text-slate-300 hover:text-white"
            >
              {theme === "dark" ? (
                <Sun className="w-4 h-4 text-amber-400" />
              ) : (
                <Moon className="w-4 h-4 text-indigo-400" />
              )}
            </button>
          </div>
        </div>
      </header>

      {/* 4. HERO SECTION WITH 3D CANVAS & TEXT */}
      <section className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-10 lg:pt-16 pb-12">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 lg:gap-6 items-center">
          {/* LEFT: TEXT & CTA */}
          <div className="lg:col-span-6 flex flex-col justify-center">
            <AnimatedHeroText onStartClick={handleScrollToCards} />
          </div>

          {/* RIGHT: 3D COMPUTER SCENE (OR CSS FALLBACK ON MOBILE) */}
          <div className="lg:col-span-6 flex items-center justify-center">
            {isMobile ? (
              // Lightweight Fallback for mobile devices to prevent any lag
              <motion.div
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                className="w-full max-w-sm aspect-[4/3] rounded-2xl bg-gradient-to-tr from-slate-900 to-slate-800 border border-pink-500/30 p-6 flex flex-col items-center justify-center text-center shadow-2xl relative"
              >
                <div className="w-16 h-16 rounded-2xl bg-pink-500/20 flex items-center justify-center text-pink-400 mb-4 animate-bounce">
                  <Laptop className="w-8 h-8" />
                </div>
                <h4 className="font-bold text-lg text-slate-100">Khung Cảnh 3D Tối Ưu Mobile</h4>
                <p className="text-xs text-slate-400 mt-2">
                  Tự động chuyển đổi chế độ siêu nhẹ để duy trì 60 FPS mượt mà trên điện thoại.
                </p>
              </motion.div>
            ) : (
              <Suspense
                fallback={
                  <div className="w-full h-[450px] flex items-center justify-center text-xs text-slate-500 font-mono">
                    Đang nạp 3D Canvas Engine...
                  </div>
                }
              >
                <Hero3DScene />
              </Suspense>
            )}
          </div>
        </div>
      </section>

      {/* 5. 3 FEATURE MODULE CARDS */}
      <FeatureCards
        onSelectServer={() => onNavigate?.("server")}
        onSelectClient={() => onNavigate?.("client")}
        onSelectTest={() => onNavigate?.("test")}
      />

      {/* 6. SYSTEM FOOTER */}
      <footer className="relative z-10 border-t border-slate-800/40 py-8 text-center text-xs text-slate-500">
        <p>Đồ Án Lập Trình Mạng Máy Tính • Nhóm 13 (X2) • Đạt chuẩn RFC 6455 & Cloud Relay</p>
      </footer>
    </div>
  );
}
