import { useState, useRef, useEffect } from 'react';
import { ExternalLink } from 'lucide-react';
import { userService } from '../../services/user';
import { 
  ZalordMessageFilledIcon,
  ZalordContactFilledIcon,
  ZalordMessageOutlineIcon, 
  ZalordContactOutlineIcon, 
  ZalordSettingsOutlineIcon,
  ZalordSettingsFilledIcon
} from './ZalordIcons';

type StoredUser = {
  displayName?: string;
  username?: string;
  phoneNumber?: string;
  avatarUrl?: string | null;
};

const getStoredUser = (): StoredUser => {
  try {
    const rawUser = localStorage.getItem('user');
    return rawUser ? JSON.parse(rawUser) : {};
  } catch {
    return {};
  }
};

const getStoredUserName = () => {
  const user = getStoredUser();
  return user.displayName || user.username || user.phoneNumber || 'Người dùng';
};

const getStoredAvatarUrl = () => getStoredUser().avatarUrl || null;

export default function SidebarNav() {
  const [activeTab, setActiveTab] = useState('message');
  const [showProfilePopup, setShowProfilePopup] = useState(false);
  const [userName, setUserName] = useState(getStoredUserName);
  const [avatarUrl, setAvatarUrl] = useState<string | null>(getStoredAvatarUrl);
  const popupRef = useRef<HTMLDivElement>(null);

  const getInitials = (name: string) => {
    const words = name.trim().split(/\s+/);
    if (words.length === 0) return "";
    if (words.length === 1) return words[0].charAt(0).toUpperCase();
    return (words[0].charAt(0) + words[1].charAt(0)).toUpperCase();
  };

  useEffect(() => {
    let cancelled = false;

    userService.me()
      .then((profile) => {
        if (cancelled) return;

        setUserName(profile.displayName);
        setAvatarUrl(profile.avatarUrl);

        const storedUser = getStoredUser();
        localStorage.setItem(
          'user',
          JSON.stringify({
            ...storedUser,
            ...profile,
            username: profile.phoneNumber,
          })
        );
      })
      .catch(() => {
        // Keep the cached identity when the profile endpoint is temporarily unavailable.
      });

    return () => {
      cancelled = true;
    };
  }, []);

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
      <div className="flex flex-col items-center w-full relative">
        {/* Avatar Area */}
        <div className="relative mt-2" ref={popupRef}>
          <div 
            onClick={() => setShowProfilePopup(!showProfilePopup)}
            className="w-10 h-10 rounded-full bg-gradient-to-b from-[#87baff] to-[#0068ff] flex items-center justify-center text-white font-medium text-[14px] overflow-hidden cursor-pointer transition-colors shadow-sm hover:opacity-90"
          >
            {avatarUrl ? (
              <img src={avatarUrl} alt={userName} className="h-full w-full object-cover" />
            ) : (
              getInitials(userName)
            )}
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
               <div 
                 onClick={() => {
                   localStorage.removeItem('token');
                   localStorage.removeItem('refreshToken');
                   localStorage.removeItem('user');
                   window.location.href = '/account/login';
                 }}
                 className="px-4 py-2.5 hover:bg-gray-100 cursor-pointer text-[14px] text-gray-700 transition-colors"
               >
                 Đăng xuất
               </div>
             </div>
          )}
        </div>
        
        {/* Top Icons */}
        <div className="flex flex-col w-full mt-6 items-center gap-0.5">
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
