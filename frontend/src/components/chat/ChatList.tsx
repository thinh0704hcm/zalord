import { useState } from 'react';
import { XCircle } from 'lucide-react';
import { ZalordAddGroupIcon, ZalordSearchIcon } from './ZalordIcons';
import CreateGroupModal from './CreateGroupModal';

interface Chat {
  id: number;
  name: string;
  message: string;
  time: string;
  unread: number;
  avatar: string;
  group?: boolean;
}

interface ChatListProps {
  chats: Chat[];
  activeChatId: number;
  onSelectChat: (id: number) => void;
}

export default function ChatList({ chats, activeChatId, onSelectChat }: ChatListProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [isSearching, setIsSearching] = useState(false);
  const [activeFilter, setActiveFilter] = useState<'all' | 'unread'>('all');
  const [isCreateGroupModalOpen, setIsCreateGroupModalOpen] = useState(false);

  const displayedChats = activeFilter === 'unread' ? chats.filter(c => c.unread > 0) : chats;

  const handleSearchFocus = () => {
    setIsSearching(true);
  };

  const closeSearch = () => {
    setIsSearching(false);
    setSearchQuery('');
  };

  return (
    <div className="w-[344px] h-screen bg-white border-r border-[#d6dbe1] flex flex-col flex-shrink-0">
      {/* Header Container */}
      <div className="flex flex-col border-b border-[#d6dbe1]">
        {/* Search Bar */}
        <div className="px-4 pt-4 pb-3 flex items-center gap-1">
          <div className="flex-1 bg-[#eaedf0] h-[32px] rounded-md flex items-center px-2.5 gap-2">
            <ZalordSearchIcon className="text-[#7589A3]" />
            <input
              type="text"
              placeholder="Tìm kiếm"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onFocus={handleSearchFocus}
              className="bg-transparent border-none outline-none w-full text-[14px] text-[#081c36] placeholder-[#7589A3]"
            />
            {searchQuery && (
              <XCircle
                size={16}
                fill="#949494"
                stroke="#eaedf0"
                className="cursor-pointer"
                onClick={() => setSearchQuery('')}
              />
            )}
          </div>
          {isSearching ? (
            <button onClick={closeSearch} className="px-1 py-1 text-[14px] font-medium text-[#081c36] hover:bg-gray-100 rounded-md">
              Đóng
            </button>
          ) : (
            <div className="flex items-center">
              <button
                title="Tạo nhóm chat"
                onClick={() => setIsCreateGroupModalOpen(true)}
                className="h-[32px] w-[32px] flex items-center justify-center text-[#081c36] hover:bg-[#eaedf0] rounded-md transition-colors"
              >
                <ZalordAddGroupIcon />
              </button>
            </div>
          )}
        </div>

        {/* Tabs */}
        {isSearching ? (
          <div className="flex items-center gap-5 px-4 pt-1 text-[14px] font-medium">
            <span className="text-[#0068ff] border-b-[2px] border-[#0068ff] pb-2 -mb-[1px] cursor-pointer">Tất cả</span>
            <span className="text-[#7589A3] pb-2 cursor-pointer hover:text-[#081c36]">Liên hệ</span>
            <span className="text-[#7589A3] pb-2 cursor-pointer hover:text-[#081c36]">Tin nhắn</span>
            <span className="text-[#7589A3] pb-2 cursor-pointer hover:text-[#081c36]">File</span>
          </div>
        ) : (
          <div className="flex items-center justify-between px-4 pt-1">
            <div className="flex items-center gap-4 text-[14px] font-medium">
              <span
                onClick={() => setActiveFilter('all')}
                className={`pb-2 cursor-pointer ${activeFilter === 'all' ? 'text-[#0068ff] border-b-[2px] border-[#0068ff] -mb-[1px]' : 'text-[#7589A3] hover:text-[#081c36]'}`}
              >
                Tất cả
              </span>
              <span
                onClick={() => setActiveFilter('unread')}
                className={`pb-2 cursor-pointer ${activeFilter === 'unread' ? 'text-[#0068ff] border-b-[2px] border-[#0068ff] -mb-[1px]' : 'text-[#7589A3] hover:text-[#081c36]'}`}
              >
                Chưa đọc
              </span>
            </div>
          </div>
        )}
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto">
        {isSearching ? (
          searchQuery === '0817920271' ? (
            <div className="p-4">
              <h3 className="font-semibold text-[14px] text-[#081c36] mb-4">Tìm bạn qua số điện thoại:</h3>
              <div
                className="flex items-center gap-3 cursor-pointer hover:bg-[#f3f5f6] p-2 -ml-2 rounded-md"
                onClick={() => {
                  onSelectChat(0);
                }}
              >
                <div className="w-[48px] h-[48px] rounded-full bg-[#0068ff] flex items-center justify-center text-white font-medium text-[16px] flex-shrink-0">
                  NT
                </div>
                <div className="flex flex-col">
                  <span className="text-[15px] text-[#081c36] font-medium mb-0.5">Nguyễn Phúc Thịnh</span>
                  <span className="text-[13px] text-gray-700">Số điện thoại: <span className="text-[#0068ff]">0817920271</span></span>
                </div>
              </div>
            </div>
          ) : (
            <div className="p-4 text-center text-gray-500 text-[13px]">
              {searchQuery ? 'Không tìm thấy kết quả' : 'Nhập từ khóa để tìm kiếm'}
            </div>
          )
        ) : (
          <div className="flex flex-col">
            {displayedChats.map(chat => (
              <div
                key={chat.id}
                onClick={() => onSelectChat(chat.id)}
                className={`flex items-start gap-3 px-2.5 py-2.5 cursor-pointer rounded-lg transition-all border ${chat.id === activeChatId ? 'bg-[#e5efff] border-transparent' : 'bg-white border-transparent hover:bg-[#f3f5f6] hover:border-[#eef0f1]'}`}
              >
                <div className="relative mt-0.5">
                  <div className="w-[46px] h-[46px] rounded-full bg-blue-100 flex items-center justify-center text-blue-600 font-semibold text-lg flex-shrink-0">
                    {chat.avatar}
                  </div>
                  {chat.unread > 0 && (
                    <div className="absolute -bottom-0.5 -right-0.5 bg-red-500 text-white text-[10px] font-bold h-[16px] min-w-[16px] px-1 rounded-full border border-white flex items-center justify-center leading-none">
                      {chat.unread > 9 ? '9+' : chat.unread}
                    </div>
                  )}
                </div>

                <div className="flex-1 min-w-0">
                  <div className="flex justify-between items-baseline mb-0.5">
                    <h3 className="font-medium text-[15px] text-gray-900 truncate pr-2">{chat.name}</h3>
                    <span className="text-[12px] text-gray-500 flex-shrink-0">{chat.time}</span>
                  </div>
                  <p className={`text-[13px] truncate leading-snug ${chat.unread > 0 ? 'text-[#081c36] font-medium' : 'text-[#7589A3]'}`}>
                    {chat.message}
                  </p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <CreateGroupModal 
        isOpen={isCreateGroupModalOpen} 
        onClose={() => setIsCreateGroupModalOpen(false)} 
      />
    </div>
  );
}
