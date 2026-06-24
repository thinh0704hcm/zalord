import { useState, useEffect, useRef } from 'react';
import EmojiPicker, { Categories } from 'emoji-picker-react';
import { UserPlus, Search, PanelRight, Smile, Users, ThumbsUp } from 'lucide-react';
import { ZalordStickerIcon, ZalordPhotoIcon, ZalordAttachIcon, ZalordNamecardIcon, ZalordScreenshotIcon, ZalordTextFormatIcon, ZalordQuickMsgIcon, ZalordBankCardIcon, ZalordMoreIcon } from './ZalordIcons';

import type { Chat } from '../../pages/chat/ChatLayout';
import { messageService } from '../../services/message';
import { conversationService } from '../../services/conversation';
import { wsService } from '../../services/websocket';

interface ChatWindowProps {
  chat?: Chat;
  onConversationReady?: (temporaryId: string | number, conversationId: string) => void;
}

const Daystamp = ({ date }: { date: string }) => {
  const getDaystampText = (dateStr: string) => {
    const messageDate = new Date(dateStr);
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);

    const isSameDay = (d1: Date, d2: Date) => 
      d1.getDate() === d2.getDate() && 
      d1.getMonth() === d2.getMonth() && 
      d1.getFullYear() === d2.getFullYear();

    if (isSameDay(messageDate, today)) {
      return "Hôm nay";
    } else if (isSameDay(messageDate, yesterday)) {
      return "Hôm qua";
    } else {
      const dd = String(messageDate.getDate()).padStart(2, '0');
      const mm = String(messageDate.getMonth() + 1).padStart(2, '0');
      const yyyy = messageDate.getFullYear();
      return `${dd}/${mm}/${yyyy}`;
    }
  };

  return (
    <div className="flex justify-center mt-1 mb-2">
      <div className="bg-black/25 text-white px-3 py-0.5 rounded-full text-[12px] font-medium">
        {getDaystampText(date)}
      </div>
    </div>
  );
};

export default function ChatWindow({ chat, onConversationReady }: ChatWindowProps) {
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const [inputText, setInputText] = useState("");
  const [userMessages, setUserMessages] = useState<{id?: string, text: string, time: string, isSender?: boolean}[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const previousChatRef = useRef<Chat | undefined>(undefined);

  const currentUserId = (() => {
    try {
      const token = localStorage.getItem('token');
      if (!token) return null;
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.sub;
    } catch {
      return null;
    }
  })();

  useEffect(() => {
    const previousChat = previousChatRef.current;
    
    if (chat?.id !== previousChat?.id) {
      const isSameTemporaryChatResolved = Boolean(
        previousChat?.pendingDirectUserId &&
        !chat?.pendingDirectUserId &&
        previousChat?.name === chat?.name
      );

      if (!isSameTemporaryChatResolved) {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setUserMessages([]);
      }

      if (chat && typeof chat.id === 'string' && !chat.isPending) {
        messageService.history(chat.id).then(res => {
          const historyMsgs = (res.items || []).map((m: any) => {
            const msgDate = new Date(m.createdAt);
            const timeStr = `${String(msgDate.getHours()).padStart(2, '0')}:${String(msgDate.getMinutes()).padStart(2, '0')}`;
            return {
              id: m.messageId,
              text: m.content,
              time: timeStr,
              isSender: currentUserId === m.senderId
            };
          }).reverse();
          setUserMessages(historyMsgs);
          setTimeout(() => {
            messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
          }, 100);
        });
      }
    }

    previousChatRef.current = chat;
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [chat, currentUserId]);

  useEffect(() => {
    // Listen to new messages via WebSocket
    const unsubscribe = wsService.onMessage((msg) => {
      // Basic check if the message belongs to current chat
      if (msg.type === 'message.created' && msg.data?.conversationId === chat?.id) {
        const now = new Date();
        const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
        setUserMessages(prev => [...prev, { 
          id: msg.data.messageId,
          text: msg.data.content, 
          time: timeStr,
          isSender: msg.data.senderId === currentUserId
        }]);
      }
    });
    
    return unsubscribe;
  }, [chat?.id, currentUserId]);

  const handleKeyDown = async (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && inputText.trim() && chat) {
      const text = inputText.trim();
      setInputText("");
      
      const now = new Date();
      const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
      
      // Optimistic update
      setUserMessages(prev => [...prev, { text: text, time: timeStr, isSender: true }]);
      
      // Send via API. A searched user opens a temporary chat first; create the
      // real DIRECT conversation only when the first message is sent.
      try {
        let conversationId = String(chat.id);

        if (chat.pendingDirectUserId) {
          const conversation = await conversationService.createDirect(chat.pendingDirectUserId);
          conversationId = conversation.id;
          onConversationReady?.(chat.id, conversation.id);
        }

        await messageService.send({
          conversationId,
          content: text
        });
      } catch (error) {
        console.error('Failed to send message', error);
      }
    }
  };

  if (!chat) {
    return <div className="flex-1 h-screen flex flex-col bg-[#eef0f1] relative items-center justify-center text-gray-500">Chọn một đoạn chat</div>;
  }

  return (
    <div className="flex-1 h-screen flex flex-col bg-[#eef0f1] relative">
      {/* Header */}
      <div className="h-[64px] bg-white border-b border-[#d6dbe1] flex items-center justify-between px-4 flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-[42px] h-[42px] rounded-full bg-blue-500 flex items-center justify-center text-white font-semibold flex-shrink-0 overflow-hidden">
            <img src={`https://ui-avatars.com/api/?name=${encodeURIComponent(chat.name)}&background=0068ff&color=fff`} alt="Avatar" className="w-full h-full object-cover" />
          </div>
          <div className="flex flex-col justify-center">
            <h2 className="font-semibold text-gray-900 text-[16px] leading-tight">{chat.name}</h2>
            <div className="flex items-center text-[13px] text-gray-500 mt-0.5">
              {chat.group ? <Users size={13} className="mr-1" /> : <UserPlus size={13} className="mr-1" />}
              <span>{chat.group ? '79 thành viên' : 'Trực tuyến'}</span>
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
        {/* Spacer to push messages to bottom */}
        <div className="flex-1 min-h-0"></div>
        {chat.name === 'KTPM2023.3' && (
          <>

        {/* Date Divider */}
        {/* eslint-disable-next-line react-hooks/purity */}
        <Daystamp date={new Date(Date.now() - 86400000).toISOString()} />

        {/* Incoming Message Group */}
        <div className="flex items-start gap-2.5 max-w-[65%]">
              <div className="w-[48px] h-[48px] rounded-full bg-[#eaedf0] flex items-center justify-center text-[#0068ff] font-semibold text-lg flex-shrink-0">
                {typeof chat.avatar === 'string' ? chat.avatar : (chat.avatar[0] || 'G')}
              </div>
          <div className="flex flex-col gap-[2px]">
            {/* Message 1 */}
            <div className="bg-white rounded-lg px-3 py-2.5 shadow-sm border border-gray-200 relative group mb-3">
              <div className="text-[12px] text-gray-500 mb-1">Ngọc Quí</div>
              
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

              {/* Reactions */}
              <div className="absolute -bottom-3 right-2 flex items-center gap-1">
                 <div className="bg-white rounded-[12px] shadow-[0_1px_2px_rgba(0,0,0,0.15)] border border-gray-100 px-1.5 py-0.5 flex items-center gap-1.5 h-[22px]">
                     <div className="flex items-center">
                         <span className="text-[14px] leading-none">❤️</span>
                     </div>
                     <span className="text-[#001a33] font-medium text-[11px] pr-0.5">1</span>
                 </div>
                 <div className="relative group/react flex items-center justify-end">
                    <div className="absolute bottom-0 right-0 w-[240px] h-[30px] hidden group-hover/react:block"></div>
                    <div className="w-[22px] h-[22px] bg-white rounded-full shadow-[0_1px_2px_rgba(0,0,0,0.15)] border border-gray-100 flex items-center justify-center cursor-pointer hover:bg-gray-50 text-gray-500 relative z-10 opacity-0 group-hover:opacity-100 transition-opacity">
                        <ThumbsUp size={12} strokeWidth={2} />
                    </div>
                 </div>
              </div>
            </div>

            {/* Message 2 */}
            <div className="bg-white rounded-lg px-3 py-2.5 shadow-sm border border-gray-200 relative group mb-3">
              <div className="text-[14px] text-gray-800 leading-snug">
                Mấy e tham khảo thử nhé
              </div>
              <div className="text-[11px] text-gray-500 mt-1">
                15:10
              </div>
              
              
              {/* Reactions */}
              <div className="absolute -bottom-3 right-2 flex items-center gap-1">
                 {/* Reaction Pill */}
                 <div className="bg-white rounded-[12px] shadow-[0_1px_2px_rgba(0,0,0,0.15)] border border-gray-100 px-1.5 py-0.5 flex items-center gap-1.5 h-[22px]">
                     <div className="flex space-x-px items-center">
                         <span className="text-[14px] leading-none">😂</span>
                         <span className="text-[14px] leading-none">😭</span>
                         <span className="text-[14px] leading-none">👍</span>
                     </div>
                     <span className="text-[#001a33] font-medium text-[11px] pr-0.5">5</span>
                 </div>
                 
                 {/* Quick React Button */}
                 <div className="relative group/react flex items-center justify-end">
                    {/* Transparent Hitbox */}
                    <div className="absolute bottom-0 right-0 w-[240px] h-[30px] hidden group-hover/react:block"></div>
                    
                    <div className="w-[22px] h-[22px] bg-white rounded-full shadow-[0_1px_2px_rgba(0,0,0,0.15)] border border-gray-100 flex items-center justify-center cursor-pointer hover:bg-gray-50 text-gray-500 relative z-10">
                        <ThumbsUp size={12} strokeWidth={2} />
                    </div>
                    
                    {/* Hover Popup */}
                    <div className="absolute bottom-full right-0 pb-1.5 hidden group-hover/react:flex z-20 w-max">
                       <div className="bg-white rounded-full shadow-[0_2px_10px_rgba(0,0,0,0.15)] border border-gray-200 px-2 py-1.5 flex items-center gap-1.5">
                           <span className="hover:scale-125 transition-transform cursor-pointer text-[22px] leading-none">👍</span>
                           <span className="hover:scale-125 transition-transform cursor-pointer text-[22px] leading-none">❤️</span>
                           <span className="hover:scale-125 transition-transform cursor-pointer text-[22px] leading-none">😂</span>
                           <span className="hover:scale-125 transition-transform cursor-pointer text-[22px] leading-none">😮</span>
                           <span className="hover:scale-125 transition-transform cursor-pointer text-[22px] leading-none">😭</span>
                           <span className="hover:scale-125 transition-transform cursor-pointer text-[22px] leading-none">😡</span>
                       </div>
                    </div>
                 </div>
              </div>
            </div>
          </div>
        </div>
          </>
        )}

        {userMessages.length > 0 && (
          <Daystamp date={new Date().toISOString()} />
        )}
        
        {/* Dynamic User Messages Container */}
        <div className="flex flex-col gap-[2px] w-full">
          {userMessages.map((msg, index) => (
            <div key={index} className={`flex w-full ${msg.isSender !== false ? 'justify-end' : 'justify-start'}`}>
              <div className={`max-w-[70%] ${msg.isSender !== false ? 'bg-[#e5efff] border-[#cce1ff]' : 'bg-white border-[#e5e7eb]'} rounded-lg px-3 py-2 shadow-sm border`}>
                <div className="text-[#081c36] text-[15px]">{msg.text}</div>
                <div className="text-[#7589A3] text-[12px] mt-1 text-right">{msg.time}</div>
              </div>
            </div>
          ))}
          <div ref={messagesEndRef} />
        </div>
      </div>

      {/* Input Area */}
      <div className="bg-white flex flex-col flex-shrink-0">
        {/* Toolbar */}
        <div className="flex items-center gap-0.5 text-[#001a33] px-1.5 h-[46px] border-t border-b border-[#d6dbe1]">
          <div title="Gửi Sticker" className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors"><ZalordStickerIcon className="w-6 h-6" /></div>
          <div title="Gửi hình ảnh" className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors"><ZalordPhotoIcon className="w-6 h-6" /></div>
          <div title="Đính kèm File" className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors"><ZalordAttachIcon className="w-6 h-6" /></div>
          <div title="Gửi danh thiếp" className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors"><ZalordNamecardIcon className="w-6 h-6" /></div>
          <div title="Chụp kèm với cửa sổ Zalo (Alt + Ctrl + S)" className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors"><ZalordScreenshotIcon className="w-6 h-6" /></div>
          <div title="Định dạng tin nhắn (Ctrl + Shift + X)" className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors"><ZalordTextFormatIcon className="w-6 h-6" /></div>
          <div title="Chèn tin nhắn nhanh" className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors"><ZalordQuickMsgIcon className="w-6 h-6" /></div>
          <div title="Gửi nhanh số tài khoản" className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors"><ZalordBankCardIcon className="w-6 h-6" /></div>
          <div title="Tùy chọn thêm" className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors"><ZalordMoreIcon className="w-6 h-6" /></div>
        </div>
        
        {/* Input Field */}
        <div className="flex items-center gap-2 px-4 py-1.5 relative">
          {showEmojiPicker && (
            <div className="absolute bottom-[100%] right-4 mb-3 z-50 w-[360px] h-[400px] bg-white shadow-[0_4px_16px_rgba(0,0,0,0.12)] rounded-lg border border-gray-200 flex flex-col overflow-hidden">
              {/* Tabs */}
              <div className="flex border-b border-gray-200 h-[48px] bg-white flex-shrink-0">
                <button className="flex-1 text-[#6e7781] font-semibold text-[14px] hover:bg-gray-50 transition-colors">STICKER</button>
                <button className="flex-1 text-[#0068ff] font-semibold text-[14px] border-b-2 border-[#0068ff] bg-white">EMOJI</button>
              </div>
              
              {/* Emoji Content */}
              <div className="flex-1 bg-white relative emoji-picker-zalo min-h-0">
                <style>{`
                  .emoji-picker-zalo aside.EmojiPickerReact.epr-main { border: none !important; border-radius: 0 !important; }
                  .emoji-picker-zalo .epr-category-nav { display: none !important; }
                  .emoji-picker-zalo .epr-header { display: none !important; }
                  .emoji-picker-zalo .epr-body { padding-top: 4px !important; }
                  .emoji-picker-zalo .epr-emoji-category-label { display: none !important; }
                `}</style>
                <EmojiPicker 
                  onEmojiClick={() => setShowEmojiPicker(false)}
                  width={360}
                  height={352}
                  searchDisabled={true}
                  skinTonesDisabled={true}
                  previewConfig={{ showPreview: false }}
                  categories={[
                    { name: 'Cảm xúc', category: Categories.SMILEYS_PEOPLE },
                    { name: 'Động vật & Thiên nhiên', category: Categories.ANIMALS_NATURE },
                    { name: 'Đồ ăn & Thức uống', category: Categories.FOOD_DRINK },
                    { name: 'Hoạt động', category: Categories.ACTIVITIES },
                    { name: 'Du lịch & Địa điểm', category: Categories.TRAVEL_PLACES },
                    { name: 'Đồ vật', category: Categories.OBJECTS },
                    { name: 'Biểu tượng', category: Categories.SYMBOLS },
                    { name: 'Cờ', category: Categories.FLAGS }
                  ]}
                />
              </div>
            </div>
          )}
          <input 
            type="text" 
            placeholder={`Nhập @, tin nhắn tới ${chat.name}`}
            value={inputText}
            onChange={(e) => setInputText(e.target.value)}
            onKeyDown={handleKeyDown}
            className="flex-1 bg-transparent border-none outline-none text-[15px] py-1 text-gray-800 placeholder-gray-500"
          />
          <div className="flex items-center gap-1 text-gray-600">
            <div 
              className={`w-8 h-8 flex items-center justify-center rounded-md cursor-pointer transition-colors ${showEmojiPicker ? 'bg-blue-50' : 'hover:bg-gray-100'}`}
              onClick={() => setShowEmojiPicker(!showEmojiPicker)}
            >
              <Smile size={24} strokeWidth={1.5} className={`${showEmojiPicker ? 'text-[#0068ff]' : 'text-gray-500'} transition-colors`} />
            </div>
            <div title="Gửi nhanh biểu tượng cảm xúc" className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors flex-shrink-0">
              <span className="text-xl hover:scale-110 transition-transform">👍</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
