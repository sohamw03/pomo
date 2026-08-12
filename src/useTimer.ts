import { useState, useEffect, useCallback, useRef } from 'react';

export function useTimer(initialSeconds: number, modeKey: string, onComplete: (isSkip?: boolean) => void) {
  const loadInitialState = () => {
    try {
      const saved = localStorage.getItem('pomo_timer_state');
      if (saved) {
        const state = JSON.parse(saved);
        if (state.modeKey === modeKey) {
          return state;
        }
      }
    } catch (e) {}
    return null;
  };

  const initialState = loadInitialState();

  const [timeLeft, setTimeLeft] = useState(() => {
    if (initialState) {
      if (initialState.isActive && initialState.endTime) {
         const rem = Math.round((initialState.endTime - Date.now()) / 1000);
         return Math.max(0, rem);
      }
      return initialState.timeLeft;
    }
    return initialSeconds;
  });

  const [isActive, setIsActive] = useState(() => {
    return initialState ? initialState.isActive : false;
  });

  const endTimeRef = useRef<number | null>(initialState ? initialState.endTime : null);
  const prevInitialSecondsRef = useRef(initialSeconds);
  const prevModeKeyRef = useRef(modeKey);

  // Handle changes to initialSeconds or mode (e.g. mode switch or preset change)
  useEffect(() => {
    if (prevInitialSecondsRef.current !== initialSeconds || prevModeKeyRef.current !== modeKey) {
      prevInitialSecondsRef.current = initialSeconds;
      prevModeKeyRef.current = modeKey;
      setTimeLeft(initialSeconds);
      if (isActive) {
        // If timer is running, seamlessly start the new countdown
        endTimeRef.current = Date.now() + initialSeconds * 1000;
        localStorage.setItem('pomo_timer_state', JSON.stringify({
          modeKey,
          isActive: true,
          timeLeft: initialSeconds,
          endTime: endTimeRef.current
        }));
      } else {
        endTimeRef.current = null;
        localStorage.setItem('pomo_timer_state', JSON.stringify({
          modeKey,
          isActive: false,
          timeLeft: initialSeconds,
          endTime: null
        }));
      }
    }
  }, [initialSeconds, modeKey, isActive]);

  useEffect(() => {
    let interval: NodeJS.Timeout | null = null;
    
    if (isActive) {
      if (endTimeRef.current === null) {
        endTimeRef.current = Date.now() + timeLeft * 1000;
        localStorage.setItem('pomo_timer_state', JSON.stringify({
          modeKey,
          isActive: true,
          timeLeft,
          endTime: endTimeRef.current
        }));
      }
      
      interval = setInterval(() => {
        if (endTimeRef.current === null) return;
        
        const now = Date.now();
        const remaining = Math.round((endTimeRef.current - now) / 1000);
        
        if (remaining <= 0) {
          // Pause the tick processing for this interval to prevent horror-movie looping
          endTimeRef.current = null;
          setTimeLeft(0);
          onComplete(false); // Triggers mode switch, which updates initialSeconds/modeKey and seamlessly starts next timer
        } else {
          setTimeLeft(remaining);
        }
      }, 100);
    } else {
      endTimeRef.current = null;
    }
    
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [isActive, onComplete]); // omitted timeLeft to avoid clearing interval every tick

  const toggleTimer = useCallback(() => {
    setIsActive((active) => {
      const nextActive = !active;
      if (!nextActive) {
        // Pausing
        localStorage.setItem('pomo_timer_state', JSON.stringify({
          modeKey,
          isActive: false,
          timeLeft: timeLeft, // Note: closure might be slightly stale if we rely on timeLeft directly
          endTime: null
        }));
      }
      return nextActive;
    });
  }, [modeKey, timeLeft]);
  
  const resetTimer = useCallback(() => {
    setIsActive(false);
    setTimeLeft(initialSeconds);
    endTimeRef.current = null;
    localStorage.setItem('pomo_timer_state', JSON.stringify({
      modeKey,
      isActive: false,
      timeLeft: initialSeconds,
      endTime: null
    }));
  }, [initialSeconds, modeKey]);
  
  const skipTimer = useCallback(() => {
    // Force active so it automatically starts the next phase when skipped
    setIsActive(true);
    endTimeRef.current = null;
    setTimeLeft(0);
    onComplete(true);
  }, [onComplete]);

  // Sync timeLeft to local storage ONLY when paused and timeLeft changes (e.g., custom duration adjusted)
  useEffect(() => {
    if (!isActive) {
      localStorage.setItem('pomo_timer_state', JSON.stringify({
        modeKey,
        isActive: false,
        timeLeft,
        endTime: null
      }));
    }
  }, [timeLeft, isActive, modeKey]);

  return { timeLeft, isActive, toggleTimer, resetTimer, skipTimer };
}
