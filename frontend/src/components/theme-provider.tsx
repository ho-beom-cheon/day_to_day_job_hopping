"use client";

import { createContext, useContext, useMemo, useSyncExternalStore, type ReactNode } from "react";

export type Theme = "light" | "dark";

type ThemeContextValue = { theme: Theme; setTheme: (theme: Theme) => void };

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const theme = useSyncExternalStore<Theme>(subscribe, readTheme, () => "light");

  const value = useMemo<ThemeContextValue>(() => ({
    theme,
    setTheme: nextTheme => {
      document.documentElement.dataset.theme = nextTheme;
      document.documentElement.style.colorScheme = nextTheme;
      localStorage.setItem("daily-career-theme", nextTheme);
      window.dispatchEvent(new Event("daily-career-theme-change"));
    },
  }), [theme]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

function subscribe(onChange: () => void) {
  window.addEventListener("daily-career-theme-change", onChange);
  window.addEventListener("storage", onChange);
  return () => {
    window.removeEventListener("daily-career-theme-change", onChange);
    window.removeEventListener("storage", onChange);
  };
}

function readTheme(): Theme {
  return document.documentElement.dataset.theme === "dark" ? "dark" : "light";
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error("useTheme must be used inside ThemeProvider");
  return context;
}
