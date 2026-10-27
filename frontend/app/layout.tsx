import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "HydroGest | Centro de atención",
  description: "Asistente de atención de HydroGest",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="es">
      <body>{children}</body>
    </html>
  );
}
