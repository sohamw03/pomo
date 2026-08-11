import React, { useState, useCallback, useRef } from 'react';
import { Play, Pause, RotateCcw, Settings2, SkipForward } from 'lucide-react';
import { useTimer } from './useTimer';

type Preset = '25/5' | '50/10' | 'custom';
type Mode = 'work' | 'break';

const beepAudioContext = new (window.AudioContext || (window as any).webkitAudioContext)();

function playAlarm() {
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
  
  // A harmonious, gentle double-chime (G5 -> C6)
  playNote(783.99, now, 1.5, 0.5); 
  playNote(1046.50, now + 0.2, 2.0, 0.5);
}

export default function App() {
  const [preset, setPreset] = useState<Preset>('25/5');
  const [mode, setMode] = useState<Mode>('work');
  const [customWork, setCustomWork] = useState<number | ''>(25);
  const [customBreak, setCustomBreak] = useState<number | ''>(5);
  const [isPlayPressed, setIsPlayPressed] = useState(false);
  
  const workDuration = preset === '25/5' ? 25 : preset === '50/10' ? 50 : (typeof customWork === 'number' ? customWork : 1);
  const breakDuration = preset === '25/5' ? 5 : preset === '50/10' ? 10 : (typeof customBreak === 'number' ? customBreak : 1);
  
  const currentDuration = mode === 'work' ? workDuration * 60 : breakDuration * 60;
  
  const handleComplete = useCallback(() => {
    playAlarm();
    setMode(m => m === 'work' ? 'break' : 'work');
  }, []);

  const { timeLeft, isActive, toggleTimer, resetTimer, skipTimer } = useTimer(currentDuration, handleComplete);
  
  // Format time
  const mins = Math.floor(timeLeft / 60);
  const secs = timeLeft % 60;
  const timeString = `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  
  const handlePresetChange = (newPreset: Preset) => {
    setPreset(newPreset);
    resetTimer();
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

  const adjustCustom = (type: 'work' | 'break', delta: number) => {
    if (type === 'work') {
      setCustomWork(w => Math.max(1, (typeof w === 'number' ? w : 1) + delta));
    } else {
      setCustomBreak(b => Math.max(1, (typeof b === 'number' ? b : 1) + delta));
    }
    if (preset === 'custom') {
      // Re-triggering reset if not active is handled by useTimer hook automatically
      // when initialSeconds dependency updates.
    }
  };

  const longPressTimeout = useRef<NodeJS.Timeout | null>(null);
  
  const handlePointerDown = () => {
    longPressTimeout.current = setTimeout(() => {
      if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen().catch(err => console.log(err));
      } else {
        document.exitFullscreen();
      }
    }, 500); // 500ms long press
  };

  const cancelLongPress = () => {
    if (longPressTimeout.current) {
      clearTimeout(longPressTimeout.current);
      longPressTimeout.current = null;
    }
  };

  // M3 Theme Colors
  const isWork = mode === 'work';
  const primaryColor = isWork ? 'text-orange-500 dark:text-orange-400' : 'text-amber-500 dark:text-amber-400';
  const primaryBg = isWork ? 'bg-orange-500 hover:bg-orange-600 dark:bg-orange-500 dark:hover:bg-orange-600' : 'bg-amber-500 hover:bg-amber-600 dark:bg-amber-500 dark:hover:bg-amber-600';
  const primaryContainer = isWork ? 'bg-orange-100 text-orange-900 dark:bg-orange-900/50 dark:text-orange-100' : 'bg-amber-100 text-amber-900 dark:bg-amber-900/50 dark:text-amber-100';

  const progressPercent = timeLeft / currentDuration;
  const radius = 135;
  const stroke = 12;
  const normalizedRadius = radius - stroke * 2;
  const circumference = normalizedRadius * 2 * Math.PI;
  const strokeDashoffset = circumference - progressPercent * circumference;

  return (
    <div className="min-h-screen bg-stone-50 dark:bg-[#151413] text-stone-900 dark:text-stone-100 flex flex-col items-center p-4 font-sans selection:bg-orange-200">
      
      {/* Top Bar */}
      <div className="w-full max-w-md flex justify-between items-center shrink-0 pt-4 px-4">
        <h1 
          className="text-2xl font-semibold tracking-tight select-none cursor-pointer"
          onPointerDown={handlePointerDown}
          onPointerUp={cancelLongPress}
          onPointerLeave={cancelLongPress}
          onContextMenu={(e) => e.preventDefault()}
        >
          Pomo
        </h1>
        <div className="flex bg-stone-200/70 dark:bg-stone-800/70 p-1 rounded-full">
            <button 
                onClick={() => handleModeChange('work')}
                className={`px-5 py-1.5 rounded-full text-sm font-semibold transition-colors ${isWork ? primaryContainer : 'text-stone-500 hover:text-stone-700 dark:text-stone-400 dark:hover:text-stone-200'}`}
            >
                Work
            </button>
            <button 
                onClick={() => handleModeChange('break')}
                className={`px-5 py-1.5 rounded-full text-sm font-semibold transition-colors ${!isWork ? primaryContainer : 'text-stone-500 hover:text-stone-700 dark:text-stone-400 dark:hover:text-stone-200'}`}
            >
                Break
            </button>
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
            className={primaryColor}
          />
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center translate-y-1">
            <span className="text-[5rem] font-light tracking-tighter tabular-nums leading-none text-stone-800 dark:text-stone-100">
                {timeString}
            </span>
            <span className="text-stone-500 dark:text-stone-400 font-medium mt-2 tracking-widest uppercase text-xs">
                {isWork ? 'Focus' : 'Relax'}
            </span>
        </div>
      </div>

      {/* Controls */}
      <div className="flex items-center justify-center gap-8 shrink-0">
        <button 
          onClick={resetTimer}
          className="w-14 h-14 rounded-full flex items-center justify-center bg-stone-200/60 text-stone-700 hover:bg-stone-300 dark:bg-stone-800/60 dark:text-stone-300 dark:hover:bg-stone-700 transition-colors shadow-sm"
          aria-label="Reset Timer"
        >
            <RotateCcw size={22} />
        </button>
        
        <button 
          onClick={handlePlayPause}
          onPointerDown={() => setIsPlayPressed(true)}
          onPointerUp={() => setIsPlayPressed(false)}
          onPointerLeave={() => setIsPlayPressed(false)}
          onPointerCancel={() => setIsPlayPressed(false)}
          className={`w-24 h-24 rounded-full flex items-center justify-center text-white shadow-lg transition-transform duration-150 ease-out ${isPlayPressed ? 'scale-90' : 'hover:scale-105 scale-100'} ${primaryBg}`}
          aria-label={isActive ? "Pause Timer" : "Start Timer"}
        >
            {isActive ? <Pause size={36} className="fill-current" /> : <Play size={36} className="fill-current translate-x-[2px]" />}
        </button>

        <button 
          onClick={skipTimer}
          className="w-14 h-14 rounded-full flex items-center justify-center bg-stone-200/60 text-stone-700 hover:bg-stone-300 dark:bg-stone-800/60 dark:text-stone-300 dark:hover:bg-stone-700 transition-colors shadow-sm"
          aria-label="Skip Phase"
        >
            <SkipForward size={22} />
        </button>
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
            label="Custom" 
            icon={<Settings2 size={16} />} 
            selected={preset === 'custom'} 
            onClick={() => handlePresetChange('custom')} 
            isWork={isWork}
         />
      </div>

      {/* Custom Settings Bottom Area */}
      <div className={`w-full max-w-md px-4 transition-all duration-300 overflow-hidden ${preset === 'custom' ? 'opacity-100 max-h-64' : 'opacity-0 max-h-0'}`}>
        <div className="bg-white dark:bg-stone-900/60 rounded-[28px] p-5 shadow-sm border border-stone-100 dark:border-stone-800/80 flex justify-around items-center">
            
            {/* Work Setting */}
            <div className="flex flex-col items-center w-1/2">
                <label className="text-[11px] font-bold text-stone-500 dark:text-stone-400 uppercase tracking-widest mb-3">Work (m)</label>
                <div className="flex items-center gap-3">
                    <button onClick={() => adjustCustom('work', -1)} className="w-10 h-10 text-xl flex items-center justify-center rounded-full bg-stone-100 text-stone-600 hover:bg-stone-200 dark:bg-stone-800 dark:text-stone-400 dark:hover:bg-stone-700 transition-colors">-</button>
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
                        className="w-12 text-center text-xl font-medium tabular-nums bg-transparent border-b-2 border-transparent focus:border-orange-400 dark:focus:border-orange-500 outline-none transition-colors"
                    />
                    <button onClick={() => adjustCustom('work', 1)} className="w-10 h-10 text-xl flex items-center justify-center rounded-full bg-stone-100 text-stone-600 hover:bg-stone-200 dark:bg-stone-800 dark:text-stone-400 dark:hover:bg-stone-700 transition-colors">+</button>
                </div>
            </div>
            
            <div className="w-px h-14 bg-stone-200 dark:bg-stone-700/50 shrink-0"></div>
            
            {/* Break Setting */}
            <div className="flex flex-col items-center w-1/2">
                <label className="text-[11px] font-bold text-stone-500 dark:text-stone-400 uppercase tracking-widest mb-3">Break (m)</label>
                <div className="flex items-center gap-3">
                    <button onClick={() => adjustCustom('break', -1)} className="w-10 h-10 text-xl flex items-center justify-center rounded-full bg-stone-100 text-stone-600 hover:bg-stone-200 dark:bg-stone-800 dark:text-stone-400 dark:hover:bg-stone-700 transition-colors">-</button>
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
                        className="w-12 text-center text-xl font-medium tabular-nums bg-transparent border-b-2 border-transparent focus:border-orange-400 dark:focus:border-orange-500 outline-none transition-colors"
                    />
                    <button onClick={() => adjustCustom('break', 1)} className="w-10 h-10 text-xl flex items-center justify-center rounded-full bg-stone-100 text-stone-600 hover:bg-stone-200 dark:bg-stone-800 dark:text-stone-400 dark:hover:bg-stone-700 transition-colors">+</button>
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
        ? 'bg-orange-100 text-orange-900 dark:bg-orange-500/20 dark:text-orange-200 border-transparent shadow-sm' 
        : 'bg-amber-100 text-amber-900 dark:bg-amber-500/20 dark:text-amber-200 border-transparent shadow-sm';
    
    const unselectedClass = 'bg-stone-100 text-stone-600 dark:bg-stone-800/80 dark:text-stone-300 hover:bg-stone-200 dark:hover:bg-stone-700 border-transparent transition-colors';

    return (
        <button 
            onClick={onClick}
            className={`flex items-center gap-2 px-4 py-2 rounded-full font-medium text-sm transition-all ${selected ? selectedClass : unselectedClass}`}
        >
            {icon}
            {label}
        </button>
    );
}
