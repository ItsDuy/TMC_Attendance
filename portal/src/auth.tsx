import { createContext, useContext, useMemo, useState, type ReactNode } from "react";
import { clearSession, getStoredUser, type SessionUser } from "./api";

type AuthContextValue = {
  user: SessionUser | null;
  setUser: (user: SessionUser | null) => void;
  signOut: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<SessionUser | null>(getStoredUser());

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      setUser: (next) => setUserState(next),
      signOut: () => {
        clearSession();
        setUserState(null);
      },
    }),
    [user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return context;
}
