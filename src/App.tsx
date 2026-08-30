import React, { useState, useCallback, useRef, useEffect } from 'react';
import { Play, Pause, RotateCcw, Settings2, SkipForward } from 'lucide-react';
import { motion } from 'motion/react';
import '@material/web/ripple/ripple.js';
import { useTimer } from './useTimer';

type Preset = '25/5' | '50/10' | 'custom';
type Mode = 'work' | 'break';

const beepAudioContext = new (window.AudioContext || (window as any).webkitAudioContext)();

function playAlarm(nextMode: 'work' | 'break') {
  if (beepAudioContext.state === 'suspended') {
    beepAudioContext.resume();
  }
  
  const playNote = (frequency: number, startTime: number, duration: number, volume: number) => {
    const osc = beepAudioContext.createOscillator();
    const gain = beepAudioContext.createGain();
    
    osc.connect(gain);
    gain.connect(beepAudioContext.destination);
    
    // Soft sine wave for a pleasant chime
    osc.type = 'sine';
    osc.frequency.setValueAtTime(frequency, startTime);
    
    // Envelope: sharp attack, smooth exponential decay
    gain.gain.setValueAtTime(0, startTime);
    gain.gain.linearRampToValueAtTime(volume, startTime + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.001, startTime + duration);
    
    osc.start(startTime);
    osc.stop(startTime + duration);
  };

  const now = beepAudioContext.currentTime;
  
  if (nextMode === 'break') {
    // A harmonious, gentle ascending double-chime (G5 -> C6)
    playNote(783.99, now, 1.5, 0.5); 
    playNote(1046.50, now + 0.2, 2.0, 0.5);
  } else {
    // A crisp, alert repeating bell (A5, A5)
    playNote(880.00, now, 1.2, 0.4);
    playNote(880.00, now + 0.15, 1.5, 0.4);
  }
}

function useAutoRepeat(action: () => void, delay = 400, interval = 100) {
  const timeoutRef = useRef<NodeJS.Timeout | null>(null);
  const intervalRef = useRef<NodeJS.Timeout | null>(null);
  const actionRef = useRef(action);
  
  useEffect(() => {
    actionRef.current = action;
  }, [action]);

  const start = useCallback((e: React.PointerEvent) => {
    if (e.pointerType === 'mouse' && e.button !== 0) return;
    actionRef.current(); 
    timeoutRef.current = setTimeout(() => {
      intervalRef.current = setInterval(() => {
        actionRef.current();
      }, interval);
    }, delay);
  }, [delay, interval]);

  const stop = useCallback(() => {
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    if (intervalRef.current) clearInterval(intervalRef.current);
  }, []);

  useEffect(() => {
    return stop;
  }, [stop]);

  return {
    onPointerDown: start,
    onPointerUp: stop,
    onPointerLeave: stop,
    onPointerCancel: stop,
    onContextMenu: (e: React.MouseEvent) => e.preventDefault()
  };
}

function useWakeLock(isActive: boolean) {
  const wakeLockRef = useRef<any>(null);
  const isDisallowedRef = useRef<boolean>(false);

  const requestWakeLock = useCallback(async () => {
    if (isDisallowedRef.current) return;
    if ('wakeLock' in navigator && document.visibilityState === 'visible') {
      try {
        if (wakeLockRef.current) {
           await wakeLockRef.current.release().catch(() => {});
        }
        wakeLockRef.current = await (navigator as any).wakeLock.request('screen');
        
        wakeLockRef.current.addEventListener('release', () => {
          // Re-acquire if it was released but we are still active and visible
          if (isActive && document.visibilityState === 'visible') {
             // Use timeout to prevent rapid looping if it repeatedly fails
             setTimeout(() => requestWakeLock(), 200);
          }
        });
      } catch (err: any) {
        if (err.name === 'NotAllowedError' || err.name === 'SecurityError') {
          // Permissions policy in iframe / environment disallowed wake lock
          isDisallowedRef.current = true;
        }
      }
    }
  }, [isActive]);

  const releaseWakeLock = useCallback(async () => {
    if (wakeLockRef.current !== null) {
      await wakeLockRef.current.release().catch(() => {});
      wakeLockRef.current = null;
    }
  }, []);

  useEffect(() => {
    if (isActive) {
      requestWakeLock();
    } else {
      releaseWakeLock();
    }
    return () => {
      releaseWakeLock();
    };
  }, [isActive, requestWakeLock, releaseWakeLock]);

  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible' && isActive) {
        // slight delay to ensure document is fully in focus and allowed to request lock
        setTimeout(() => {
          requestWakeLock();
        }, 100);
      }
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [isActive, requestWakeLock]);
}

type RippleMotionButtonProps = React.ComponentPropsWithoutRef<typeof motion.button>;

function RippleMotionButton({ children, className, disabled, ...props }: RippleMotionButtonProps) {
  return (
    <motion.button
      {...props}
      disabled={disabled}
      className={'relative overflow-hidden ' + (className ?? '')}
      onContextMenu={(event) => {
        props.onContextMenu?.(event);
        event.preventDefault();
      }}
    >
      {React.createElement('md-ripple', {
        'aria-hidden': true,
        disabled: Boolean(disabled),
        style: {
          '--md-ripple-hover-color': 'currentColor',
          '--md-ripple-pressed-color': 'currentColor',
        } as React.CSSProperties,
      })}
      {children}
    </motion.button>
  );
}


export default function App() {
  const [preset, setPreset] = useState<Preset>(() => {
    const saved = localStorage.getItem('pomo_preset');
    return (saved as Preset) || '25/5';
  });
  const [mode, setMode] = useState<Mode>(() => {
    const saved = localStorage.getItem('pomo_mode');
    return (saved as Mode) || 'work';
  });
  const [customWork, setCustomWork] = useState<number | ''>(() => {
    const saved = localStorage.getItem('pomo_customWork');
    return saved ? parseInt(saved, 10) : 25;
  });
  const [customBreak, setCustomBreak] = useState<number | ''>(() => {
    const saved = localStorage.getItem('pomo_customBreak');
    return saved ? parseInt(saved, 10) : 5;
  });
  const [isCustomExpanded, setIsCustomExpanded] = useState(false);

  useEffect(() => {
    localStorage.setItem('pomo_preset', preset);
  }, [preset]);

  useEffect(() => {
    localStorage.setItem('pomo_mode', mode);
  }, [mode]);

  useEffect(() => {
    if (typeof customWork === 'number') {
      localStorage.setItem('pomo_customWork', customWork.toString());
    }
  }, [customWork]);

  useEffect(() => {
    if (typeof customBreak === 'number') {
      localStorage.setItem('pomo_customBreak', customBreak.toString());
    }
  }, [customBreak]);

  const workDecRepeat = useAutoRepeat(() => adjustCustom('work', -1));
  const workIncRepeat = useAutoRepeat(() => adjustCustom('work', 1));
  const breakDecRepeat = useAutoRepeat(() => adjustCustom('break', -1));
  const breakIncRepeat = useAutoRepeat(() => adjustCustom('break', 1));
  
  const workDuration = preset === '25/5' ? 25 : preset === '50/10' ? 50 : (typeof customWork === 'number' ? customWork : 1);
  const breakDuration = preset === '25/5' ? 5 : preset === '50/10' ? 10 : (typeof customBreak === 'number' ? customBreak : 1);
  
  const currentDuration = mode === 'work' ? workDuration * 60 : breakDuration * 60;

  const handleComplete = useCallback((isSkip?: boolean) => {
    if (isSkip) {
      setMode(m => m === 'work' ? 'break' : 'work');
    } else {
      setMode(m => {
        const nextMode = m === 'work' ? 'break' : 'work';
        playAlarm(nextMode);
        setTimeout(() => {
          setMode(currentM => {
            // Only switch if we haven't already manually changed the mode via skip
            if (currentM === m) return nextMode;
            return currentM;
          });
        }, 2500); // 2.5 second delay before switching visually and starting next
        return m; // keep current mode during the delay
      });
    }
  }, []);

  const { timeLeft, isActive, toggleTimer, resetTimer, skipTimer } = useTimer(currentDuration, mode, handleComplete);
  
  useWakeLock(isActive);

  // Format time
  const mins = Math.floor(timeLeft / 60);
  const secs = timeLeft % 60;
  const timeString = `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  
  const handlePresetChange = (newPreset: Preset) => {
    if (newPreset === 'custom') {
      if (preset === 'custom') {
        setIsCustomExpanded(!isCustomExpanded);
      } else {
        setPreset('custom');
        setIsCustomExpanded(true);
        resetTimer();
      }
    } else {
      setPreset(newPreset);
      setIsCustomExpanded(false);
      resetTimer();
    }
  };

  const handleModeChange = (newMode: Mode) => {
    setMode(newMode);
    resetTimer();
  };

  const handlePlayPause = () => {
    if (beepAudioContext.state === 'suspended') {
      beepAudioContext.resume();
    }
    toggleTimer();
  };

  const handleReset = () => {
    resetTimer();
  };

  const handleSkip = () => {
    skipTimer();
  };

  const adjustCustom = (type: 'work' | 'break', delta: number) => {
    if (type === 'work') {
      setCustomWork(w => Math.max(1, (typeof w === 'number' ? w : 1) + delta));
    } else {
      setCustomBreak(b => Math.max(1, (typeof b === 'number' ? b : 1) + delta));
    }
  };

  // Fullscreen long-press handler for "Pomo" title
  const longPressTimerRef = useRef<NodeJS.Timeout | null>(null);

  const getIsFullscreen = () => {
    return !!(
      document.fullscreenElement ||
      (document as any).webkitFullscreenElement ||
      (document as any).mozFullScreenElement ||
      (document as any).msFullscreenElement
    );
  };

  const toggleFullscreenMode = async () => {
    try {
      const isFs = getIsFullscreen();
      if (!isFs) {
        const docEl = document.documentElement as any;
        if (docEl.requestFullscreen) {
          await docEl.requestFullscreen();
        } else if (docEl.webkitRequestFullscreen) {
          await docEl.webkitRequestFullscreen();
        } else if (docEl.mozRequestFullScreen) {
          await docEl.mozRequestFullScreen();
        } else if (docEl.msRequestFullscreen) {
          await docEl.msRequestFullscreen();
        }
      } else {
        const doc = document as any;
        if (doc.exitFullscreen) {
          await doc.exitFullscreen();
        } else if (doc.webkitExitFullscreen) {
          await doc.webkitExitFullscreen();
        } else if (doc.mozCancelFullScreen) {
          await doc.mozCancelFullScreen();
        } else if (doc.msExitFullscreen) {
          await doc.msExitFullscreen();
        }
      }
    } catch (err) {
      console.log('Fullscreen toggle notification:', err);
    }
  };

  const handlePomoPointerDown = () => {
    if (longPressTimerRef.current) {
      clearTimeout(longPressTimerRef.current);
    }
    longPressTimerRef.current = setTimeout(() => {
      toggleFullscreenMode();
      longPressTimerRef.current = null;
    }, 600);
  };

  const handlePomoPointerUp = () => {
    if (longPressTimerRef.current) {
      clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }
  };

  // M3 Theme Colors
  const isWork = mode === 'work';
  const primaryRingColor = isWork ? 'text-[#ffdcc2] dark:text-[#6a3900]' : 'text-[#f9e287] dark:text-[#534600]';
  const primaryBg = isWork ? 'bg-[#ffdcc2] dark:bg-[#6a3900]' : 'bg-[#f9e287] dark:bg-[#534600]';
  const primaryContainer = isWork ? 'bg-[#ffdcc2] text-[#2d1600] dark:bg-[#6a3900] dark:text-[#ffdcc2]' : 'bg-[#f9e287] text-[#221b00] dark:bg-[#534600] dark:text-[#f9e287]';
  const primaryOnColor = isWork ? 'text-[#2d1600] dark:text-[#ffdcc2]' : 'text-[#221b00] dark:text-[#f9e287]';
  const primaryFocus = isWork ? 'focus:border-[#c47732] dark:focus:border-[#ffb77d]' : 'focus:border-[#c6a900] dark:focus:border-[#e9c400]';

  const progressPercent = timeLeft / currentDuration;
  const radius = 180;
  const stroke = 14;
  const normalizedRadius = radius - stroke * 2;
  const circumference = normalizedRadius * 2 * Math.PI;
  const strokeDashoffset = circumference - progressPercent * circumference;
  const nubAngle = progressPercent * Math.PI * 2;
  const nubX = radius + normalizedRadius * Math.cos(nubAngle);
  const nubY = radius + normalizedRadius * Math.sin(nubAngle);

  return (
    <div 
      className="fixed inset-0 overflow-x-hidden overflow-y-auto bg-stone-50 dark:bg-[#151413] text-stone-900 dark:text-stone-100 flex flex-col items-center px-4 font-sans selection:bg-[#ffdcc2] select-none"
      style={{
        paddingTop: 'max(1rem, env(safe-area-inset-top))',
        paddingBottom: 'max(1rem, env(safe-area-inset-bottom))'
      }}
    >
      
      {/* Top Bar */}
      <div className="w-full max-w-md flex justify-between items-center shrink-0 pt-4 px-4">
        <h1 
          className="text-2xl font-semibold tracking-tight select-none cursor-pointer active:scale-95 transition-transform"
          onPointerDown={handlePomoPointerDown}
          onPointerUp={handlePomoPointerUp}
          onPointerLeave={handlePomoPointerUp}
          onPointerCancel={handlePomoPointerUp}
          onContextMenu={(e) => e.preventDefault()}
          title="Hold to toggle fullscreen"
        >
          Pomo
        </h1>
        <div className="flex bg-stone-200/70 dark:bg-stone-800/70 p-1 rounded-full">
            <RippleMotionButton
                onClick={() => handleModeChange('work')}
                className={`px-5 py-1.5 rounded-full text-sm font-semibold transition-colors cursor-pointer select-none touch-manipulation ${isWork ? primaryContainer : 'text-stone-500 hover:text-stone-700 dark:text-stone-400 dark:hover:text-stone-200'}`}
            >
                Work
            </RippleMotionButton>
            <RippleMotionButton
                onClick={() => handleModeChange('break')}
                className={`px-5 py-1.5 rounded-full text-sm font-semibold transition-colors cursor-pointer select-none touch-manipulation ${!isWork ? primaryContainer : 'text-stone-500 hover:text-stone-700 dark:text-stone-400 dark:hover:text-stone-200'}`}
            >
                Break
            </RippleMotionButton>
        </div>
      </div>

      {/* Main Centered Area */}
      <div className="flex-1 flex flex-col items-center justify-center w-full min-h-0 gap-10 pb-4">
          {/* Timer Circle */}
          <div className="relative flex items-center justify-center shrink-0">
            <svg
          height={radius * 2}
          width={radius * 2}
          className="transform -rotate-90 drop-shadow-sm"
        >
          <circle
            stroke="currentColor"
            fill="transparent"
            strokeWidth={stroke}
            r={normalizedRadius}
            cx={radius}
            cy={radius}
            className="text-stone-200 dark:text-stone-800/80"
          />
          <circle
            stroke="currentColor"
            fill="transparent"
            strokeWidth={stroke}
            strokeDasharray={circumference + ' ' + circumference}
            style={{ strokeDashoffset, transition: 'stroke-dashoffset 0.1s linear' }}
            strokeLinecap="round"
            r={normalizedRadius}
            cx={radius}
            cy={radius}
            className={primaryRingColor}
          />
          {progressPercent > 0 && (
            <circle
              cx={nubX}
              cy={nubY}
              r={stroke / 2}
              fill="currentColor"
              className={`${primaryOnColor} opacity-75`}
            />
          )}
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center translate-y-1.5">
            <span className="text-[4.75rem] font-light tracking-tighter tabular-nums leading-none text-stone-800 dark:text-stone-100">
                {timeString}
            </span>
            <span className="text-stone-500 dark:text-stone-400 font-medium mt-2 tracking-widest uppercase text-xs">
                {isWork ? 'Focus' : 'Relax'}
            </span>
        </div>
      </div>

      {/* Controls */}
      <div className="flex items-center justify-center gap-8 shrink-0">
        <RippleMotionButton
          onClick={handleReset}
          className="w-14 h-14 rounded-full flex items-center justify-center bg-stone-200/60 text-stone-700 dark:bg-stone-800/60 dark:text-stone-300 shadow-sm hover:bg-stone-300 dark:hover:bg-stone-700 cursor-pointer select-none touch-manipulation"
          aria-label="Reset Timer"
        >
            <RotateCcw size={22} />
        </RippleMotionButton>
        
        <RippleMotionButton
          onClick={handlePlayPause}
          className={`w-24 h-24 rounded-full flex items-center justify-center ${primaryOnColor} shadow-lg cursor-pointer select-none touch-manipulation hover:opacity-95 ${primaryBg}`}
          aria-label={isActive ? "Pause Timer" : "Start Timer"}
        >
          {isActive ? <Pause size={36} className="fill-current" /> : <Play size={36} className="fill-current translate-x-[2px]" />}
        </RippleMotionButton>

        <RippleMotionButton
          onClick={handleSkip}
          className="w-14 h-14 rounded-full flex items-center justify-center bg-stone-200/60 text-stone-700 dark:bg-stone-800/60 dark:text-stone-300 shadow-sm hover:bg-stone-300 dark:hover:bg-stone-700 cursor-pointer select-none touch-manipulation"
          aria-label="Skip Phase"
        >
            <SkipForward size={22} />
        </RippleMotionButton>
      </div>
      </div>

      {/* Bottom Area */}
      <div className="w-full max-w-md shrink-0 flex flex-col items-center pb-6">
          {/* Presets - M3 segmented choice chips */}
          <div className="w-full flex gap-2 justify-center mb-6 flex-wrap">
         <Chip 
            label="25 / 5" 
            selected={preset === '25/5'} 
            onClick={() => handlePresetChange('25/5')} 
            isWork={isWork}
         />
         <Chip 
            label="50 / 10" 
            selected={preset === '50/10'} 
            onClick={() => handlePresetChange('50/10')} 
            isWork={isWork}
         />
         <Chip 
            label={`${customWork || 1} / ${customBreak || 1}`} 
            icon={<Settings2 size={16} />} 
            selected={preset === 'custom'} 
            onClick={() => handlePresetChange('custom')} 
            isWork={isWork}
         />
      </div>

      {/* Custom Settings Bottom Area */}
      <div className={`w-full max-w-md px-4 transition-all duration-300 overflow-hidden ${isCustomExpanded ? 'opacity-100 max-h-64' : 'opacity-0 max-h-0'}`}>
        <div className="bg-white dark:bg-stone-900/60 rounded-[28px] p-5 shadow-sm border border-stone-100 dark:border-stone-800/80 flex justify-around items-center">
            
            {/* Work Setting */}
            <div className="flex flex-col items-center w-1/2">
                <label className="text-[11px] font-bold text-stone-500 dark:text-stone-400 uppercase tracking-widest mb-3">Work (m)</label>
                <div className="flex items-center gap-3">
                    <RippleMotionButton
                      {...workDecRepeat} 
                      className="w-10 h-10 text-xl flex items-center justify-center rounded-full bg-stone-100 text-stone-600 hover:bg-stone-200 dark:bg-stone-800 dark:text-stone-400 dark:hover:bg-stone-700 transition-colors cursor-pointer select-none touch-manipulation"
                    >
                      -
                    </RippleMotionButton>
                    <input 
                        type="text" 
                        inputMode="numeric" 
                        pattern="[0-9]*"
                        value={customWork}
                        onChange={(e) => {
                            const val = e.target.value;
                            if (val === '') setCustomWork('');
                            else {
                                const num = parseInt(val, 10);
                                if (!isNaN(num) && num >= 0 && num <= 999) setCustomWork(num);
                            }
                        }}
                        onBlur={() => {
                            if (customWork === '' || customWork < 1) setCustomWork(1);
                        }}
                        className={`w-12 text-center text-xl font-medium tabular-nums bg-transparent border-b-2 border-transparent ${primaryFocus} outline-none transition-colors select-text`}
                    />
                    <RippleMotionButton
                      {...workIncRepeat} 
                      className="w-10 h-10 text-xl flex items-center justify-center rounded-full bg-stone-100 text-stone-600 hover:bg-stone-200 dark:bg-stone-800 dark:text-stone-400 dark:hover:bg-stone-700 transition-colors cursor-pointer select-none touch-manipulation"
                    >
                      +
                    </RippleMotionButton>
                </div>
            </div>
            
            <div className="w-px h-14 bg-stone-200 dark:bg-stone-700/50 shrink-0"></div>
            
            {/* Break Setting */}
            <div className="flex flex-col items-center w-1/2">
                <label className="text-[11px] font-bold text-stone-500 dark:text-stone-400 uppercase tracking-widest mb-3">Break (m)</label>
                <div className="flex items-center gap-3">
                    <RippleMotionButton
                      {...breakDecRepeat} 
                      className="w-10 h-10 text-xl flex items-center justify-center rounded-full bg-stone-100 text-stone-600 hover:bg-stone-200 dark:bg-stone-800 dark:text-stone-400 dark:hover:bg-stone-700 transition-colors cursor-pointer select-none touch-manipulation"
                    >
                      -
                    </RippleMotionButton>
                    <input 
                        type="text" 
                        inputMode="numeric" 
                        pattern="[0-9]*"
                        value={customBreak}
                        onChange={(e) => {
                            const val = e.target.value;
                            if (val === '') setCustomBreak('');
                            else {
                                const num = parseInt(val, 10);
                                if (!isNaN(num) && num >= 0 && num <= 999) setCustomBreak(num);
                            }
                        }}
                        onBlur={() => {
                            if (customBreak === '' || customBreak < 1) setCustomBreak(1);
                        }}
                        className={`w-12 text-center text-xl font-medium tabular-nums bg-transparent border-b-2 border-transparent ${primaryFocus} outline-none transition-colors select-text`}
                    />
                    <RippleMotionButton
                      {...breakIncRepeat} 
                      className="w-10 h-10 text-xl flex items-center justify-center rounded-full bg-stone-100 text-stone-600 hover:bg-stone-200 dark:bg-stone-800 dark:text-stone-400 dark:hover:bg-stone-700 transition-colors cursor-pointer select-none touch-manipulation"
                    >
                      +
                    </RippleMotionButton>
                </div>
            </div>

        </div>
      </div>
      </div>

    </div>
  );
}

function Chip({ label, icon, selected, onClick, isWork }: { label: string, icon?: React.ReactNode, selected: boolean, onClick: () => void, isWork: boolean }) {
    const selectedClass = isWork 
        ? 'bg-[#ffdcc2] text-[#2d1600] dark:bg-[#6a3900] dark:text-[#ffdcc2] border-transparent shadow-sm'
        : 'bg-[#f9e287] text-[#221b00] dark:bg-[#534600] dark:text-[#f9e287] border-transparent shadow-sm';
    
    const unselectedClass = 'bg-stone-100 text-stone-600 dark:bg-stone-800/80 dark:text-stone-300 hover:bg-stone-200 dark:hover:bg-stone-700 border-transparent transition-colors';

    return (
        <RippleMotionButton
            onClick={onClick}
            className={`flex items-center gap-2 px-4 py-2 rounded-full font-medium text-sm transition-colors cursor-pointer select-none touch-manipulation ${selected ? selectedClass : unselectedClass}`}
        >
            {icon}
            {label}
        </RippleMotionButton>
    );
}
