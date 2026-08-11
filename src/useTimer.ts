import { useState, useEffect, useCallback, useRef } from 'react';

export function useTimer(initialSeconds: number, onComplete: () => void) {
  const [timeLeft, setTimeLeft] = useState(initialSeconds);
  const [isActive, setIsActive] = useState(false);
  const endTimeRef = useRef<number | null>(null);

  // Reset time left when initialSeconds changes, but only if the timer is not actively running.
  useEffect(() => {
    if (!isActive) {
      setTimeLeft(initialSeconds);
    }
  }, [initialSeconds, isActive]);

  useEffect(() => {
    let interval: NodeJS.Timeout | null = null;
    
    if (isActive) {
      if (endTimeRef.current === null) {
        endTimeRef.current = Date.now() + timeLeft * 1000;
      }
      
      interval = setInterval(() => {
        const now = Date.now();
        const remaining = Math.round((endTimeRef.current! - now) / 1000);
        
        if (remaining <= 0) {
          setTimeLeft(0);
          setIsActive(false);
          endTimeRef.current = null;
          onComplete();
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
  }, [isActive, onComplete]); // Intentionally omitting timeLeft so it doesn't re-trigger the effect

  const toggleTimer = useCallback(() => setIsActive((active) => !active), []);
  
  const resetTimer = useCallback(() => {
    setIsActive(false);
    setTimeLeft(initialSeconds);
    endTimeRef.current = null;
  }, [initialSeconds]);
  
  const skipTimer = useCallback(() => {
    setIsActive(false);
    setTimeLeft(0);
    endTimeRef.current = null;
    onComplete();
  }, [onComplete]);

  return { timeLeft, isActive, toggleTimer, resetTimer, skipTimer };
}
