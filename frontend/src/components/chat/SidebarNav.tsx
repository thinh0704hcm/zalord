import { MessageCircle, Contact2, CheckSquare, Cloud, Briefcase, Settings } from 'lucide-react';

export default function SidebarNav() {
  return (
    <div className="w-[64px] h-screen bg-[#005ae0] flex flex-col items-center py-4 justify-between z-10 flex-shrink-0">
      <div className="flex flex-col items-center gap-4 w-full">
        {/* Avatar */}
        <div className="w-11 h-11 rounded-full bg-blue-300 border border-white flex items-center justify-center text-white font-semibold text-base overflow-hidden cursor-pointer">
          NT
        </div>
        
        {/* Top Icons */}
        <div className="flex flex-col w-full mt-2 items-center">
          <div className="w-[64px] h-[64px] flex items-center justify-center bg-[#004bb9] cursor-pointer text-white">
            <MessageCircle size={26} strokeWidth={1.5} />
          </div>
          <div className="w-[64px] h-[64px] flex items-center justify-center hover:bg-[#004bb9] cursor-pointer text-white/80 hover:text-white transition-colors">
            <Contact2 size={26} strokeWidth={1.5} />
          </div>
          <div className="w-[64px] h-[64px] flex items-center justify-center hover:bg-[#004bb9] cursor-pointer text-white/80 hover:text-white transition-colors">
            <CheckSquare size={26} strokeWidth={1.5} />
          </div>
        </div>
      </div>

      <div className="flex flex-col items-center w-full mb-2">
        {/* Bottom Icons */}
        <div className="w-[64px] h-[64px] flex items-center justify-center hover:bg-[#004bb9] cursor-pointer text-white/80 hover:text-white transition-colors">
          <Cloud size={26} strokeWidth={1.5} />
        </div>
        <div className="w-[64px] h-[64px] flex items-center justify-center hover:bg-[#004bb9] cursor-pointer text-white/80 hover:text-white transition-colors">
          <Briefcase size={26} strokeWidth={1.5} />
        </div>
        <div className="w-[64px] h-[64px] flex items-center justify-center hover:bg-[#004bb9] cursor-pointer text-white/80 hover:text-white transition-colors">
          <Settings size={26} strokeWidth={1.5} />
        </div>
      </div>
    </div>
  );
}
