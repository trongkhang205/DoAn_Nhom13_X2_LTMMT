"use client";

import React, { useRef, useMemo } from "react";
import { Canvas, useFrame } from "@react-three/fiber";
import { Float, MeshWobbleMaterial, RoundedBox } from "@react-three/drei";
import * as THREE from "three";

/**
 * Low-Poly 3D Laptop / Monitor with Glowing Screen & Data Lines
 */
function FloatingComputer({ mouse }: { mouse: React.MutableRefObject<{ x: number; y: number }> }) {
  const groupRef = useRef<THREE.Group>(null);
  const screenGlowRef = useRef<THREE.Mesh>(null);

  // Parallax and smooth hover rotation
  useFrame((state, delta) => {
    if (!groupRef.current) return;
    const targetRotX = mouse.current.y * 0.35 + 0.1;
    const targetRotY = mouse.current.x * 0.45;
    
    groupRef.current.rotation.x = THREE.MathUtils.damp(
      groupRef.current.rotation.x,
      targetRotX,
      4,
      delta
    );
    groupRef.current.rotation.y = THREE.MathUtils.damp(
      groupRef.current.rotation.y,
      targetRotY,
      4,
      delta
    );

    // Screen light pulsating effect
    if (screenGlowRef.current) {
      const material = screenGlowRef.current.material as THREE.MeshStandardMaterial;
      material.emissiveIntensity = 0.8 + Math.sin(state.clock.elapsedTime * 3) * 0.25;
    }
  });

  return (
    <group ref={groupRef} position={[0, -0.2, 0]} scale={1.15}>
      {/* LAPTOP BASE */}
      <RoundedBox args={[3.2, 0.12, 2.2]} radius={0.06} smoothness={4} position={[0, -0.06, 0.3]}>
        <meshStandardMaterial
          color="#1e1828"
          roughness={0.25}
          metalness={0.8}
        />
      </RoundedBox>

      {/* TRACKPAD */}
      <mesh position={[0, 0.01, 1.05]}>
        <planeGeometry args={[0.9, 0.55]} />
        <meshStandardMaterial color="#2d223c" roughness={0.4} metalness={0.5} />
      </mesh>

      {/* KEYBOARD RECESS */}
      <mesh position={[0, 0.01, 0.05]}>
        <planeGeometry args={[2.8, 1.1]} />
        <meshStandardMaterial color="#161020" roughness={0.6} />
      </mesh>

      {/* LAPTOP SCREEN LID (Tilted back ~105 deg) */}
      <group position={[0, 0, -0.75]} rotation={[-1.85, 0, 0]}>
        {/* Lid Casing */}
        <RoundedBox args={[3.2, 2.1, 0.08]} radius={0.06} smoothness={4} position={[0, 1.05, 0]}>
          <meshStandardMaterial color="#1a1424" roughness={0.25} metalness={0.85} />
        </RoundedBox>

        {/* Display Screen Outer Bezel */}
        <mesh position={[0, 1.05, 0.045]}>
          <planeGeometry args={[3.0, 1.9]} />
          <meshStandardMaterial color="#0c0a12" roughness={0.9} />
        </mesh>

        {/* Glowing Terminal Display Canvas */}
        <mesh ref={screenGlowRef} position={[0, 1.05, 0.05]}>
          <planeGeometry args={[2.85, 1.75]} />
          <meshStandardMaterial
            color="#0f172a"
            emissive="#ec4899"
            emissiveIntensity={0.8}
            roughness={0.2}
            metalness={0.3}
          />
        </mesh>

        {/* Remote Desktop Header Bar on Screen */}
        <mesh position={[0, 1.8, 0.052]}>
          <planeGeometry args={[2.7, 0.12]} />
          <meshBasicMaterial color="#f43f5e" />
        </mesh>
      </group>

      {/* REVOLVING CYBER RING 1 */}
      <mesh rotation={[Math.PI / 3, 0, 0]} position={[0, 0.8, 0]}>
        <torusGeometry args={[2.5, 0.012, 16, 100]} />
        <MeshWobbleMaterial color="#f43f5e" factor={0.2} speed={1.5} wireframe />
      </mesh>

      {/* REVOLVING CYBER RING 2 */}
      <mesh rotation={[-Math.PI / 4, Math.PI / 6, 0]} position={[0, 0.8, 0]}>
        <torusGeometry args={[2.8, 0.01, 16, 100]} />
        <MeshWobbleMaterial color="#38bdf8" factor={0.15} speed={2} wireframe />
      </mesh>
    </group>
  );
}

/**
 * Floating Network Starfield / Data Particles
 */
function ParticleCloud({ count = 180 }: { count?: number }) {
  const pointsRef = useRef<THREE.Points>(null);

  const [positions, colors] = useMemo(() => {
    const pos = new Float32Array(count * 3);
    const col = new Float32Array(count * 3);

    const palette = [
      new THREE.Color("#f43f5e"), // Rose
      new THREE.Color("#ec4899"), // Pink
      new THREE.Color("#38bdf8"), // Cyan
      new THREE.Color("#c084fc"), // Lavender
    ];

    for (let i = 0; i < count; i++) {
      pos[i * 3] = (Math.random() - 0.5) * 12;
      pos[i * 3 + 1] = (Math.random() - 0.5) * 8;
      pos[i * 3 + 2] = (Math.random() - 0.5) * 8;

      const c = palette[Math.floor(Math.random() * palette.length)];
      col[i * 3] = c.r;
      col[i * 3 + 1] = c.g;
      col[i * 3 + 2] = c.b;
    }
    return [pos, col];
  }, [count]);

  useFrame((state, delta) => {
    if (!pointsRef.current) return;
    pointsRef.current.rotation.y += delta * 0.06;
    pointsRef.current.rotation.x = Math.sin(state.clock.elapsedTime * 0.2) * 0.08;
  });

  return (
    <points ref={pointsRef}>
      <bufferGeometry>
        <bufferAttribute
          attach="attributes-position"
          count={positions.length / 3}
          array={positions}
          itemSize={3}
        />
        <bufferAttribute
          attach="attributes-color"
          count={colors.length / 3}
          array={colors}
          itemSize={3}
        />
      </bufferGeometry>
      <pointsMaterial
        size={0.07}
        vertexColors
        transparent
        opacity={0.85}
        blending={THREE.AdditiveBlending}
      />
    </points>
  );
}

export interface Hero3DSceneProps {
  className?: string;
}

/**
 * 3D Hero Scene with Mouse Parallax, Floating Computer, & Particle Grid
 */
export default function Hero3DScene({ className = "" }: Hero3DSceneProps) {
  const mouse = useRef({ x: 0, y: 0 });

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
    const y = -(((e.clientY - rect.top) / rect.height) * 2 - 1);
    mouse.current = { x, y };
  };

  return (
    <div
      className={`relative w-full h-[450px] md:h-[550px] lg:h-[620px] overflow-hidden select-none ${className}`}
      onMouseMove={handleMouseMove}
    >
      <Canvas
        camera={{ position: [0, 1.2, 5.5], fov: 42 }}
        dpr={[1, 2]}
        gl={{ antialias: true, alpha: true, powerPreference: "high-performance" }}
      >
        <ambientLight intensity={0.8} />
        <directionalLight position={[5, 6, 4]} intensity={1.5} color="#fff0f5" />
        <pointLight position={[-4, 3, 2]} intensity={2.2} color="#ec4899" />
        <pointLight position={[4, -2, 2]} intensity={2.0} color="#38bdf8" />

        <Float speed={2.2} rotationIntensity={0.25} floatIntensity={0.4}>
          <FloatingComputer mouse={mouse} />
        </Float>

        <ParticleCloud count={180} />
      </Canvas>
    </div>
  );
}
