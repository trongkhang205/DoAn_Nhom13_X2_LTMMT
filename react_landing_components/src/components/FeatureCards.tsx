"use client";

import React, { useRef } from "react";
import { motion, useMotionTemplate, useMotionValue, useSpring } from "framer-motion";
import { Server, Monitor, Zap, ArrowRight, Shield, RefreshCw, Cpu } from "lucide-react";

export interface FeatureCardData {
  id: string;
  tag: string;
  tagColor: string;
  title: string;
  desc: string;
  features: string[];
  btnLabel: string;
  icon: React.ComponentType<{ className?: string }>;
  accentColor: string;
  onClick: () => void;
}

interface TiltCardProps {
  card: FeatureCardData;
  index: number;
}

/**
 * Single 3D Tilt Card with dynamic cursor border-glow & floating icon
 */
function TiltCard({ card, index }: TiltCardProps) {
  const cardRef = useRef<HTMLDivElement>(null);

  // Mouse coordinates inside card for 3D tilt & border glow
  const mouseX = useMotionValue(0);
  const mouseY = useMotionValue(0);

  // Smooth springs for rotation
  const rotateX = useSpring(useMotionValue(0), { damping: 20, stiffness: 200 });
  const rotateY = useSpring(useMotionValue(0), { damping: 20, stiffness: 200 });

  function handleMouseMove(e: React.MouseEvent<HTMLDivElement>) {
    if (!cardRef.current) return;
    const rect = cardRef.current.getBoundingClientRect();
    const width = rect.width;
    const height = rect.height;

    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    // Normalizing -0.5 to +0.5
    const xPct = x / width - 0.5;
    const yPct = y / height - 0.5;

    // Tilt limits: ±12 deg
    rotateX.set(-yPct * 20);
    rotateY.set(xPct * 20);

    mouseX.set(x);
    mouseY.set(y);
  }

  function handleMouseLeave() {
    rotateX.set(0);
    rotateY.set(0);
    mouseX.set(-1000);
    mouseY.set(-1000);
  }

  const radialGlow = useMotionTemplate`radial-gradient(350px circle at ${mouseX}px ${mouseY}px, ${card.accentColor}33, transparent 80%)`;

  const IconComponent = card.icon;

  return (
    <motion.div
      ref={cardRef}
      initial={{ opacity: 0, y: 35 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-50px" }}
      transition={{ duration: 0.6, delay: index * 0.15, ease: [0.16, 1, 0.3, 1] }}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      style={{
        rotateX,
        rotateY,
        transformStyle: "preserve-3d",
      }}
      className="group relative rounded-2xl bg-slate-900/70 border border-slate-800/80 p-7 flex flex-col justify-between backdrop-blur-xl transition-colors duration-300 hover:border-slate-700/80 shadow-[0_15px_35px_rgba(0,0,0,0.3)] hover:shadow-[0_20px_50px_rgba(0,0,0,0.45)]"
    >
      {/* MOUSE-FOLLOWING RADIAL BORDER GLOW */}
      <motion.div
        className="pointer-events-none absolute -inset-px rounded-2xl opacity-0 transition-opacity duration-300 group-hover:opacity-100"
        style={{ background: radialGlow }}
      />

      <div style={{ transform: "translateZ(30px)" }}>
        {/* TOP ROW: ICON & TAG */}
        <div className="flex items-center justify-between mb-6">
          <motion.div
            animate={{ y: [0, -6, 0] }}
            transition={{ duration: 3.5, repeat: Infinity, ease: "easeInOut", delay: index * 0.4 }}
            className={`w-14 h-14 rounded-xl flex items-center justify-center bg-gradient-to-br ${card.tagColor} shadow-lg`}
          >
            <IconComponent className="w-7 h-7 text-white" />
          </motion.div>

          <span className="text-[11px] font-bold tracking-wider uppercase px-3 py-1 rounded-full bg-slate-800/80 text-slate-300 border border-slate-700/60">
            {card.tag}
          </span>
        </div>

        {/* TITLE & DESCRIPTION */}
        <h3 className="text-xl font-bold text-slate-100 mb-3 group-hover:text-pink-400 transition-colors duration-200">
          {card.title}
        </h3>
        <p className="text-sm text-slate-400 leading-relaxed mb-6">
          {card.desc}
        </p>

        {/* FEATURES CHECKLIST */}
        <ul className="space-y-2.5 mb-8 text-xs text-slate-300">
          {card.features.map((feat, fIdx) => (
            <li key={fIdx} className="flex items-center gap-2">
              <span className="text-emerald-400 font-bold">✓</span>
              <span>{feat}</span>
            </li>
          ))}
        </ul>
      </div>

      {/* ACTION BUTTON */}
      <div style={{ transform: "translateZ(25px)" }}>
        <button
          onClick={card.onClick}
          className="w-full py-3.5 px-5 rounded-xl font-semibold text-xs tracking-wide uppercase flex items-center justify-center gap-2 text-white bg-slate-800/80 hover:bg-gradient-to-r hover:from-rose-500 hover:to-pink-600 border border-slate-700/60 hover:border-transparent transition-all duration-300 shadow-md group-hover:shadow-[0_0_20px_rgba(244,63,94,0.35)]"
        >
          <span>{card.btnLabel}</span>
          <ArrowRight className="w-3.5 h-3.5 transition-transform group-hover:translate-x-1 duration-200" />
        </button>
      </div>
    </motion.div>
  );
}

export interface FeatureCardsProps {
  onSelectServer?: () => void;
  onSelectClient?: () => void;
  onSelectTest?: () => void;
  className?: string;
}

export default function FeatureCards({
  onSelectServer,
  onSelectClient,
  onSelectTest,
  className = "",
}: FeatureCardsProps) {
  const cards: FeatureCardData[] = [
    {
      id: "cardServer",
      tag: "MÁY BỊ ĐIỀU KHIỂN",
      tagColor: "from-rose-500 to-pink-600",
      accentColor: "#f43f5e",
      title: "Khởi Chạy Máy Chủ (Server)",
      desc: "Cấu hình cho phép máy tính này phát màn hình và tiếp nhận quyền điều khiển từ xa. Cung cấp IP LAN, Device ID và Mật khẩu kết nối bảo mật.",
      features: [
        "Hiển thị IP LAN, Device ID 9 số & Mật khẩu",
        "Bật/Tắt Cầu Nối 4G Cloudflare 1-Click",
        "Nhật ký kết nối (Server Logs) thời gian thực",
      ],
      btnLabel: "Mở Bảng Điều Khiển Server",
      icon: Server,
      onClick: () => onSelectServer?.(),
    },
    {
      id: "cardClient",
      tag: "MÁY ĐI ĐIỀU KHIỂN",
      tagColor: "from-sky-500 to-indigo-600",
      accentColor: "#38bdf8",
      title: "Khởi Chạy Máy Khách (Client)",
      desc: "Nhập địa chỉ IP hoặc Device ID để xem màn hình và điều khiển máy tính khác từ xa qua mạng LAN hoặc Internet mà không cần mở port modem.",
      features: [
        "Tự động quét tìm máy chủ trong mạng LAN",
        "Điều khiển chuột, bàn phím & clipboard 2 chiều",
        "Truyền tệp 128KB Multi-file & Tùy chọn 60 FPS HD",
      ],
      btnLabel: "Mở Bảng Kết Nối Client",
      icon: Monitor,
      onClick: () => onSelectClient?.(),
    },
    {
      id: "cardTest",
      tag: "CHẠY KIỂM THỬ NHANH",
      tagColor: "from-amber-500 to-rose-600",
      accentColor: "#fbbf24",
      title: "Chạy Test Localhost",
      desc: "Tự động kết nối với chính máy này (Loopback) để kiểm tra ngay tính năng truyền màn hình, chuột và bàn phím mà không cần gõ mật khẩu.",
      features: [
        "Kiểm thử tức thì chỉ với 1 click duy nhất",
        "Tự động điền IP 127.0.0.1 và Pass máy chủ",
        "Trải nghiệm khung nhìn Canvas điều khiển trực tiếp",
      ],
      btnLabel: "Bắt Đầu Test Localhost ⚡",
      icon: Zap,
      onClick: () => onSelectTest?.(),
    },
  ];

  return (
    <section id="featuresSection" className={`relative z-10 w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16 ${className}`}>
      {/* SECTION HEADER */}
      <div className="text-center max-w-3xl mx-auto mb-14">
        <span className="text-xs font-bold tracking-widest text-pink-400 uppercase mb-2 block">
          MODULES CHỨC NĂNG
        </span>
        <h2 className="text-2xl sm:text-3xl font-extrabold text-slate-100 tracking-tight">
          Chọn Chế Độ Vận Hành Hệ Thống
        </h2>
        <p className="mt-3 text-sm text-slate-400">
          Khởi chạy máy chủ để chia sẻ màn hình hoặc dùng máy khách kết nối điều khiển an toàn qua trình duyệt.
        </p>
      </div>

      {/* 3D TILT CARDS GRID */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8" style={{ perspective: "1200px" }}>
        {cards.map((card, idx) => (
          <TiltCard key={card.id} card={card} index={idx} />
        ))}
      </div>
    </section>
  );
}
