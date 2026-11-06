import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { clearSession, getStoredUser, refreshProfile, type SessionUser } from "./api";

type AuthContextValue = {
  user: SessionUser | null;
  setUser: (user: SessionUser | null) => void;
  signOut: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<SessionUser | null>(getStoredUser());

  // Sessions saved before profiles existed have no name yet: fetch it once.
  useEffect(() => {
    if (user && user.employeeId && !user.fullName) {
      refreshProfile(user).then(setUserState);
    }
  }, [user]);

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
