import { UserPlus, Search, PanelRight, Smile, Image as ImageIcon, Paperclip, Monitor, Type, AtSign, Zap, MoreHorizontal, ThumbsUp, Users } from 'lucide-react';

export default function ChatWindow() {
  return (
    <div className="flex-1 h-screen flex flex-col bg-[#eef0f1]">
      {/* Header */}
      <div className="h-[64px] bg-white border-b border-gray-200 flex items-center justify-between px-4 flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-[42px] h-[42px] rounded-full bg-blue-500 flex items-center justify-center text-white font-semibold flex-shrink-0 overflow-hidden">
            <img src="https://ui-avatars.com/api/?name=KTPM2023.3&background=0068ff&color=fff" alt="Avatar" className="w-full h-full object-cover" />
          </div>
          <div className="flex flex-col justify-center">
            <h2 className="font-semibold text-gray-900 text-[16px] leading-tight">KTPM2023.3</h2>
            <div className="flex items-center text-[13px] text-gray-500 mt-0.5">
              <Users size={13} className="mr-1" />
              <span>79 thành viên</span>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-1 text-gray-600">
          <button className="p-1.5 hover:bg-gray-100 rounded-md transition-colors"><UserPlus size={18} /></button>
          <button className="p-1.5 hover:bg-gray-100 rounded-md transition-colors"><Search size={18} /></button>
          <button className="p-1.5 hover:bg-gray-100 rounded-md transition-colors"><PanelRight size={18} /></button>
        </div>
      </div>

      {/* Messages Area */}
      <div className="flex-1 overflow-y-auto px-4 py-2 flex flex-col gap-3">
        {/* System Message */}
        <div className="flex justify-center mt-2">
          <div className="bg-[#e4e8eb] px-3 py-1 rounded-full text-[12px] text-gray-700 shadow-sm">
            Sử dụng Zalo PC để tìm tin nhắn trước ngày 06/06/2026. <a href="#" className="text-[#0068ff] hover:underline">Tải Zalo PC</a>
          </div>
        </div>

        {/* Date Divider */}
        <div className="flex justify-center mt-1 mb-2">
          <div className="bg-[#e4e8eb] text-gray-600 px-3 py-0.5 rounded-full text-[12px] font-medium">
            Hôm qua
          </div>
        </div>

        {/* Message 1 */}
        <div className="flex items-start gap-2.5 max-w-[65%]">
          <div className="w-[38px] h-[38px] rounded-full bg-gray-300 flex-shrink-0 overflow-hidden mt-1">
            <img src="https://ui-avatars.com/api/?name=Ngoc+Qui&background=random" alt="Avatar" className="w-full h-full object-cover" />
          </div>
          <div className="flex flex-col relative">
            <span className="text-[12px] text-gray-500 mb-0.5 ml-1">Ngọc Quí</span>
            <div className="bg-white rounded-lg p-2.5 shadow-sm border border-gray-100 relative group">
              {/* Link Preview */}
              <div className="mb-2">
                <a href="#" className="text-[#0068ff] hover:underline break-all text-[14px] leading-relaxed">
                  https://se.uit.edu.vn/vi/tin-tức/12-su-kien-noi-bat...1ển-game-trên-nền-tảng-roblox.html
                </a>
              </div>
              <div className="bg-gray-50 border-l-[3px] border-gray-300 p-2 text-sm rounded-r-md">
                <div className="font-semibold text-gray-800 text-[13px] mb-0.5 line-clamp-1">Đăng ký tham gia lớp pilot môn học SE370 - Phát triển...</div>
                <div className="text-gray-600 line-clamp-2 text-[12px] leading-snug">Kênh thông tin khoa Công Nghệ Phần Mềm, ĐH CNTT. locate. Phòng E7.2, tòa nhà E, trường Đại học Công Nghệ...</div>
                <div className="text-[#0068ff] text-[11px] mt-1 hover:underline cursor-pointer">se.uit.edu.vn</div>
              </div>

              {/* Reaction Box */}
              <div className="absolute -bottom-2.5 right-2 bg-white rounded-full shadow border border-gray-200 px-1.5 py-0.5 flex items-center gap-0.5 text-[11px]">
                <span className="text-red-500 text-[10px]">❤️</span> <span className="text-gray-600 mx-0.5">4</span> <span className="text-gray-400 border-l border-gray-200 pl-1 text-[10px]">👍</span>
              </div>
            </div>
          </div>
        </div>

        {/* Message 2 */}
        <div className="flex items-start gap-2.5 max-w-[65%] mt-2">
          <div className="w-[38px] h-[38px] rounded-full flex-shrink-0 opacity-0"></div>
          <div className="flex flex-col relative">
            <div className="bg-white rounded-lg px-3 py-2.5 shadow-sm border border-gray-100 relative group">
              <div className="text-[14px] text-gray-800 leading-snug">
                Mấy e tham khảo thử nhé
              </div>
              <div className="text-[11px] text-gray-400 mt-1">
                15:10
              </div>
              {/* Reaction Box */}
              <div className="absolute -bottom-2.5 right-2 bg-white rounded-full shadow border border-gray-200 px-1.5 py-0.5 flex items-center gap-0.5 text-[11px]">
                <span className="text-red-500 text-[10px]">❤️</span> <span className="text-gray-600 mx-0.5">1</span> <span className="text-gray-400 border-l border-gray-200 pl-1 text-[10px]">👍</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Input Area */}
      <div className="bg-white border-t border-gray-200 flex flex-col flex-shrink-0">
        {/* Toolbar */}
        <div className="flex items-center gap-3 text-gray-600 px-4 pt-2.5 pb-1">
          <Smile size={20} className="cursor-pointer hover:text-gray-800 transition-colors" />
          <ImageIcon size={20} className="cursor-pointer hover:text-gray-800 transition-colors" />
          <Paperclip size={20} className="cursor-pointer hover:text-gray-800 transition-colors" />
          <Monitor size={20} className="cursor-pointer hover:text-gray-800 transition-colors" />
          <div className="w-[1px] h-4 bg-gray-300 mx-0.5"></div>
          <Type size={18} className="cursor-pointer hover:text-gray-800 transition-colors" />
          <AtSign size={18} className="cursor-pointer hover:text-gray-800 transition-colors" />
          <Zap size={18} className="cursor-pointer hover:text-gray-800 transition-colors text-yellow-500" />
          <MoreHorizontal size={18} className="cursor-pointer hover:text-gray-800 transition-colors" />
        </div>
        
        {/* Input Field */}
        <div className="flex items-center gap-2 px-4 pb-3">
          <input 
            type="text" 
            placeholder="Nhập @, tin nhắn tới KTPM2023.3" 
            className="flex-1 bg-transparent border-none outline-none text-[14px] py-1.5 text-gray-800 placeholder-gray-400"
          />
          <div className="flex items-center gap-3 text-gray-500">
            <Smile size={22} className="cursor-pointer hover:text-[#0068ff] transition-colors" />
            <ThumbsUp size={22} className="cursor-pointer hover:text-[#0068ff] text-yellow-500 transition-colors" />
          </div>
        </div>
      </div>
    </div>
  );
}
