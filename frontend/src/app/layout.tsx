import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";

import { NOME_PRODUTO } from "@/lib/marca";

import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: `${NOME_PRODUTO} · Gestão de e-commerce`,
  description: `${NOME_PRODUTO} — resultado, operação e pendências num só lugar.`,
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="pt-BR"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
