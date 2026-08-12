import { useState, useEffect, useCallback, useRef } from 'react';

export function useTimer(initialSeconds: number, modeKey: string, onComplete: (isSkip?: boolean) => void) {
  const [timeLeft, setTimeLeft] = useState(initialSeconds);
  const [isActive, setIsActive] = useState(false);
  const endTimeRef = useRef<number | null>(null);

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
      } else {
        endTimeRef.current = null;
      }
    }
  }, [initialSeconds, modeKey, isActive]);

  useEffect(() => {
    let interval: NodeJS.Timeout | null = null;
    
    if (isActive) {
      if (endTimeRef.current === null) {
        endTimeRef.current = Date.now() + timeLeft * 1000;
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

  const toggleTimer = useCallback(() => setIsActive((active) => !active), []);
  
  const resetTimer = useCallback(() => {
    setIsActive(false);
    setTimeLeft(initialSeconds);
    endTimeRef.current = null;
  }, [initialSeconds]);
  
  const skipTimer = useCallback(() => {
    // Force active so it automatically starts the next phase when skipped
    setIsActive(true);
    endTimeRef.current = null;
    setTimeLeft(0);
    onComplete(true);
  }, [onComplete]);

  return { timeLeft, isActive, toggleTimer, resetTimer, skipTimer };
}
