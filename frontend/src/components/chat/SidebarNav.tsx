import { useState, useRef, useEffect } from 'react';
import { ExternalLink } from 'lucide-react';
import { 
  ZalordMessageFilledIcon,
  ZalordContactFilledIcon,
  ZalordMessageOutlineIcon, 
  ZalordContactOutlineIcon, 
  ZalordSettingsOutlineIcon,
  ZalordSettingsFilledIcon
} from './ZalordIcons';

export default function SidebarNav() {
  const [activeTab, setActiveTab] = useState('message');
  const [showProfilePopup, setShowProfilePopup] = useState(false);
  const popupRef = useRef<HTMLDivElement>(null);

  const userName = "Mockee";
  
  const getInitials = (name: string) => {
    const words = name.trim().split(/\s+/);
    if (words.length === 0) return "";
    if (words.length === 1) return words[0].charAt(0).toUpperCase();
    return (words[0].charAt(0) + words[1].charAt(0)).toUpperCase();
  };

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (popupRef.current && !popupRef.current.contains(event.target as Node)) {
        setShowProfilePopup(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <div className="w-[64px] h-screen bg-[#005ae0] flex flex-col items-center py-4 justify-between z-10 flex-shrink-0">
      <div className="flex flex-col items-center gap-4 w-full relative">
        {/* Avatar Area */}
        <div className="relative" ref={popupRef}>
          <div 
            onClick={() => setShowProfilePopup(!showProfilePopup)}
            className="w-11 h-11 rounded-full bg-gradient-to-b from-[#87baff] to-[#0068ff] flex items-center justify-center text-white font-semibold text-base overflow-hidden cursor-pointer shadow-sm"
          >
            {getInitials(userName)}
          </div>

          {/* Profile Popup */}
          {showProfilePopup && (
             <div className="absolute top-0 left-[60px] w-[260px] bg-white rounded-md shadow-[0_2px_10px_rgba(0,0,0,0.2)] border border-gray-200 py-1.5 z-50">
               <div className="px-4 py-2">
                 <h3 className="font-bold text-[16px] text-gray-900">{userName}</h3>
               </div>
               <div className="h-px bg-gray-200 my-1"></div>
               <div className="flex flex-col">
                 <div className="px-4 py-2.5 hover:bg-gray-100 cursor-pointer text-[14px] text-gray-700 flex justify-between items-center transition-colors">
                   <span>Nâng cấp tài khoản</span>
                   <ExternalLink size={16} className="text-gray-500" />
                 </div>
                 <div className="px-4 py-2.5 hover:bg-gray-100 cursor-pointer text-[14px] text-gray-700 transition-colors">
                   Hồ sơ của bạn
                 </div>
                 <div className="px-4 py-2.5 hover:bg-gray-100 cursor-pointer text-[14px] text-gray-700 transition-colors">
                   Cài đặt
                 </div>
               </div>
               <div className="h-px bg-gray-200 my-1"></div>
               <div className="px-4 py-2.5 hover:bg-gray-100 cursor-pointer text-[14px] text-gray-700 transition-colors">
                 Đăng xuất
               </div>
             </div>
          )}
        </div>
        
        {/* Top Icons */}
        <div className="flex flex-col w-full mt-4 items-center gap-1.5">
          <div 
            onClick={() => setActiveTab('message')}
            className={`w-[48px] h-[48px] rounded-xl flex items-center justify-center cursor-pointer transition-colors ${activeTab === 'message' ? 'bg-[#004bb9] text-white' : 'hover:bg-[#0051d1] text-white'}`}
          >
            {activeTab === 'message' ? <ZalordMessageFilledIcon /> : <ZalordMessageOutlineIcon />}
          </div>
          <div 
            onClick={() => setActiveTab('contact')}
            className={`w-[48px] h-[48px] rounded-xl flex items-center justify-center cursor-pointer transition-colors ${activeTab === 'contact' ? 'bg-[#004bb9] text-white' : 'hover:bg-[#0051d1] text-white'}`}
          >
            {activeTab === 'contact' ? <ZalordContactFilledIcon /> : <ZalordContactOutlineIcon />}
          </div>
        </div>
      </div>

      <div className="flex flex-col items-center w-full mb-2 gap-1.5">
        {/* Bottom Icons */}

        <div 
          onClick={() => setActiveTab('settings')}
          className={`w-[48px] h-[48px] rounded-xl flex items-center justify-center cursor-pointer transition-colors ${activeTab === 'settings' ? 'bg-[#004bb9] text-white' : 'hover:bg-[#0051d1] text-white'}`}
        >
          {activeTab === 'settings' ? <ZalordSettingsFilledIcon /> : <ZalordSettingsOutlineIcon />}
        </div>
      </div>
    </div>
  );
}
