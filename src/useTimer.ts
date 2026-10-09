import { useState, useEffect, useLayoutEffect, useCallback, useRef } from 'react';

type SavedState = {
  modeKey: string;
  isActive: boolean;
  timeLeft: number;
  remainingMs?: number;
  endTime?: number | null;
};

const STORAGE_KEY = 'pomo_timer_state';
const TICK_MS = 100;

function loadSavedState(modeKey: string): SavedState | null {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      const state = JSON.parse(saved);
      if (state.modeKey === modeKey) {
        return state;
      }
    }
  } catch {}
  return null;
}

function remainingSec(remainingMs: number): number {
  return Math.max(0, Math.ceil(remainingMs / 1000));
}

export function useTimer(initialSeconds: number, modeKey: string, onComplete: (isSkip?: boolean) => void) {
  const initialState = loadSavedState(modeKey);

  const getInitialRemainingMs = () => {
    if (initialState) {
      if (initialState.isActive && initialState.endTime) {
        // Reload while running: wall-clock deadline is the only thing that survives reload.
        return Math.max(0, initialState.endTime - Date.now());
      }
      if (!initialState.isActive && typeof initialState.remainingMs === 'number') {
        return Math.max(0, initialState.remainingMs);
      }
      if (typeof initialState.timeLeft === 'number') {
        return Math.max(0, initialState.timeLeft * 1000);
      }
    }
    return initialSeconds * 1000;
  };

  const [timeLeft, setTimeLeft] = useState(() => {
    if (initialState) {
      if (initialState.isActive && initialState.endTime) {
        return remainingSec(initialState.endTime - Date.now());
      }
      if (!initialState.isActive && typeof initialState.remainingMs === 'number') {
        return remainingSec(initialState.remainingMs);
      }
      return initialState.timeLeft;
    }
    return initialSeconds;
  });

  const [isActive, setIsActive] = useState(() => {
    return initialState ? initialState.isActive : false;
  });

  // Exact ms remaining at pause / init. Source of truth for resume.
  const remainingMsRef = useRef<number>(getInitialRemainingMs());
  // Deadlines while running. endPerfRef (monotonic) drives the display;
  // endWallRef (wall clock) exists only so a reload can restore the session.
  const endPerfRef = useRef<number | null>(null);
  const endWallRef = useRef<number | null>(initialState?.endTime ?? null);
  const prevInitialSecondsRef = useRef(initialSeconds);
  const prevModeKeyRef = useRef(modeKey);
  const onCompleteRef = useRef(onComplete);
  onCompleteRef.current = onComplete;

  const persistActive = useCallback((remainingMs: number, secs: number) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      modeKey,
      isActive: true,
      timeLeft: secs,
      remainingMs,
      endTime: endWallRef.current
    }));
  }, [modeKey]);

  const persistPaused = useCallback((remainingMs: number, secs: number) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      modeKey,
      isActive: false,
      timeLeft: secs,
      remainingMs,
      endTime: null
    }));
  }, [modeKey]);

  const setDeadlines = useCallback((remainingMs: number) => {
    remainingMsRef.current = remainingMs;
    endPerfRef.current = performance.now() + remainingMs;
    endWallRef.current = Date.now() + remainingMs;
  }, []);

  const clearDeadlines = useCallback(() => {
    endPerfRef.current = null;
    endWallRef.current = null;
  }, []);

  const syncDisplayFromDeadline = useCallback(() => {
    if (endPerfRef.current === null) return;
    const remainingMs = Math.max(0, endPerfRef.current - performance.now());
    const secs = remainingSec(remainingMs);
    if (secs <= 0) {
      clearDeadlines();
      remainingMsRef.current = 0;
      setTimeLeft(0);
      onCompleteRef.current(false);
    } else {
      setTimeLeft(secs);
    }
  }, [clearDeadlines]);

  // Handle changes to initialSeconds or mode (e.g. mode switch or preset change)
  useLayoutEffect(() => {
    if (prevInitialSecondsRef.current !== initialSeconds || prevModeKeyRef.current !== modeKey) {
      prevInitialSecondsRef.current = initialSeconds;
      prevModeKeyRef.current = modeKey;
      setTimeLeft(initialSeconds);
      if (isActive) {
        // Seamlessly start the new countdown (full exact duration)
        setDeadlines(initialSeconds * 1000);
        persistActive(initialSeconds * 1000, initialSeconds);
      } else {
        clearDeadlines();
        remainingMsRef.current = initialSeconds * 1000;
        persistPaused(remainingMsRef.current, initialSeconds);
      }
    }
  }, [initialSeconds, modeKey, isActive, setDeadlines, clearDeadlines, persistActive, persistPaused]);

  useEffect(() => {
    if (!isActive) {
      clearDeadlines();
      return;
    }

    if (endPerfRef.current === null) {
      if (remainingMsRef.current <= 0) {
        // Completion/skip pending mode switch: wait for new duration instead of starting stale timer
        return;
      }
      setDeadlines(remainingMsRef.current);
      persistActive(remainingMsRef.current, remainingSec(remainingMsRef.current));
    }

    const interval = setInterval(syncDisplayFromDeadline, TICK_MS);

    const handleVisibility = () => {
      if (document.visibilityState === 'visible') {
        // Interval may have been throttled while hidden (up to 1/min);
        // snap display to the monotonic deadline immediately instead of waiting.
        syncDisplayFromDeadline();
      } else {
        // Save exact position so a reload while hidden restores precisely.
        if (endPerfRef.current !== null) {
          const remainingMs = Math.max(0, endPerfRef.current - performance.now());
          remainingMsRef.current = remainingMs;
          endWallRef.current = Date.now() + remainingMs;
          persistActive(remainingMs, remainingSec(remainingMs));
        }
      }
    };
    document.addEventListener('visibilitychange', handleVisibility);

    return () => {
      clearInterval(interval);
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, [isActive, syncDisplayFromDeadline, setDeadlines, persistActive, clearDeadlines]);

  const toggleTimer = useCallback(() => {
    if (isActive) {
      // Pausing: capture exact ms from the monotonic clock (immune to system-clock jumps,
      // no rounding loss). Resume uses this, not the rounded display value.
      const remainingMs = endPerfRef.current !== null
        ? Math.max(0, endPerfRef.current - performance.now())
        : remainingMsRef.current;
      remainingMsRef.current = remainingMs;
      const secs = remainingSec(remainingMs);
      clearDeadlines();
      setTimeLeft(secs);
      setIsActive(false);
      persistPaused(remainingMs, secs);
    } else {
      // Resuming: deadlines are derived from remainingMsRef in the effect (exact ms)
      setIsActive(true);
    }
  }, [isActive, persistPaused, clearDeadlines]);

  const resetTimer = useCallback((nextSeconds = initialSeconds, nextModeKey = modeKey) => {
    setIsActive(false);
    setTimeLeft(nextSeconds);
    remainingMsRef.current = nextSeconds * 1000;
    clearDeadlines();
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      modeKey: nextModeKey,
      isActive: false,
      timeLeft: nextSeconds,
      remainingMs: remainingMsRef.current,
      endTime: null
    }));
  }, [initialSeconds, modeKey, clearDeadlines]);

  const skipTimer = useCallback(() => {
    // Force active so it automatically starts the next phase when skipped
    setIsActive(true);
    clearDeadlines();
    remainingMsRef.current = 0;
    setTimeLeft(0);
    onCompleteRef.current(true);
  }, [clearDeadlines]);

  // Sync paused position to storage when display changes while paused (e.g. duration edited)
  useEffect(() => {
    if (!isActive) {
      persistPaused(remainingMsRef.current, timeLeft);
    }
  }, [timeLeft, isActive, persistPaused]);

  return { timeLeft, isActive, toggleTimer, resetTimer, skipTimer };
}
