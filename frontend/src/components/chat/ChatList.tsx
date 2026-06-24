import { useState, useEffect } from 'react';
import { XCircle } from 'lucide-react';
import { ZalordAddGroupIcon, ZalordSearchIcon } from './ZalordIcons';
import type { Chat } from '../../pages/chat/ChatLayout';
import CreateGroupModal from './CreateGroupModal';
import { userService } from '../../services/user';
import type { UserProfile } from '../../services/user';

interface ChatListProps {
  chats: Chat[];
  activeChatId: string | number;
  onSelectChat: (id: string | number) => void;
  onCreateGroup?: (groupName: string, memberIds: string[], selectedAvatars: string[], totalMembers: number) => Promise<void> | void;
  onStartDirectChat?: (user: UserProfile) => void;
  isLoading?: boolean;
  error?: string;
}

export default function ChatList({ chats, activeChatId, onSelectChat, onCreateGroup, onStartDirectChat, isLoading = false, error = '' }: ChatListProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [isSearching, setIsSearching] = useState(false);
  const [activeFilter, setActiveFilter] = useState<'all' | 'unread'>('all');
  const [isCreateGroupModalOpen, setIsCreateGroupModalOpen] = useState(false);
  const [searchedUser, setSearchedUser] = useState<UserProfile | null>(null);
  const [isSearchingApi, setIsSearchingApi] = useState(false);
  const [searchError, setSearchError] = useState('');

  const displayedChats = activeFilter === 'unread' ? chats.filter(c => c.unread > 0) : chats;

  useEffect(() => {
    if (!searchQuery.trim()) {
      setSearchedUser(null);
      setSearchError('');
      setIsSearchingApi(false);
      return;
    }

    setIsSearchingApi(true);
    setSearchError('');
    setSearchedUser(null);

    const timer = setTimeout(async () => {
      try {
        const profile = await userService.findByPhone(searchQuery.trim());
        setSearchedUser(profile);
      } catch (err: any) {
        setSearchError(err.message || 'Không tìm thấy kết quả');
      } finally {
        setIsSearchingApi(false);
      }
    }, 1000);

    return () => clearTimeout(timer);
  }, [searchQuery]);

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
          <div className="p-4">
            {isSearchingApi ? (
              <div className="text-center text-gray-500 text-[13px]">Đang tìm kiếm...</div>
            ) : searchedUser ? (
              <>
                <h3 className="font-semibold text-[14px] text-[#081c36] mb-4">Tìm bạn qua số điện thoại:</h3>
                <div
                  className="flex items-center gap-3 cursor-pointer hover:bg-[#f3f5f6] p-2 -ml-2 rounded-md"
                  onClick={() => {
                    onStartDirectChat?.(searchedUser);
                    closeSearch();
                  }}
                >
                  {searchedUser.avatarUrl ? (
                    <img src={searchedUser.avatarUrl} alt="avatar" className="w-[48px] h-[48px] rounded-full object-cover flex-shrink-0" />
                  ) : (
                    <div className="w-[48px] h-[48px] rounded-full bg-[#0068ff] flex items-center justify-center text-white font-medium text-[16px] flex-shrink-0">
                      {normalizeAvatarText(searchedUser.displayName)}
                    </div>
                  )}
                  <div className="flex flex-col">
                    <span className="text-[15px] text-[#081c36] font-medium mb-0.5">{searchedUser.displayName}</span>
                    <span className="text-[13px] text-gray-700">Số điện thoại: <span className="text-[#0068ff]">{searchedUser.phoneNumber}</span></span>
                  </div>
                </div>
              </>
            ) : (
              <div className="text-center text-gray-500 text-[13px]">
                {searchQuery ? searchError || 'Không tìm thấy kết quả' : 'Nhập số điện thoại để tìm kiếm'}
              </div>
            )}
          </div>
        ) : isLoading ? (
          <div className="p-4 text-center text-gray-500 text-[13px]">Đang tải danh sách chat...</div>
        ) : error ? (
          <div className="p-4 text-center text-red-500 text-[13px]">{error}</div>
        ) : displayedChats.length === 0 ? (
          <div className="p-4 text-center text-gray-500 text-[13px]">Chưa có cuộc trò chuyện</div>
        ) : (
          <div className="flex flex-col">
            {displayedChats.map(chat => (
              <div
                key={chat.id}
                onClick={() => onSelectChat(chat.id)}
                className={`flex items-start gap-3 px-2.5 py-2.5 cursor-pointer rounded-lg transition-all border ${chat.id === activeChatId ? 'bg-[#e5efff] border-transparent' : 'bg-white border-transparent hover:bg-[#f3f5f6] hover:border-[#eef0f1]'}`}
              >
                <div className="relative mt-0.5">
                  {renderAvatar(chat)}
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
        onCreateGroup={async (name, memberIds, avatars, total) => {
          await onCreateGroup?.(name, memberIds, avatars, total);
          setIsCreateGroupModalOpen(false);
        }}
      />
    </div>
  );
}

const normalizeAvatarText = (value?: string) => {
  if (!value) return '';
  const words = value.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return `${words[0][0]}${words[words.length - 1][0]}`.toUpperCase();
};

const renderAvatar = (chat: Chat) => {
  if (typeof chat.avatar === 'string') {
    return (
      <div className="w-[46px] h-[46px] rounded-full bg-blue-100 flex items-center justify-center text-blue-600 font-semibold text-lg flex-shrink-0">
        {normalizeAvatarText(chat.avatar)}
      </div>
    );
  }

  const avatars = chat.avatar;
  const total = chat.totalMembers || avatars.length;

  if (total === 3) {
    return (
      <div className="w-[46px] h-[46px] relative flex-shrink-0">
        <div className="absolute bottom-0 right-0 w-[26px] h-[26px] bg-[#a3c9ff] rounded-full flex items-center justify-center text-[10px] font-medium text-white border-[1.5px] border-white z-10 overflow-hidden">
          {normalizeAvatarText(avatars[2]) || '??'}
        </div>
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[26px] h-[26px] bg-[#0068ff] rounded-full flex items-center justify-center text-[10px] font-medium text-white border-[1.5px] border-white z-20 overflow-hidden">
          {normalizeAvatarText(avatars[1]) || '??'}
        </div>
        <div className="absolute bottom-0 left-0 w-[26px] h-[26px] bg-[#0055d4] rounded-full flex items-center justify-center text-[10px] font-medium text-white border-[1.5px] border-white z-30 overflow-hidden">
          {normalizeAvatarText(avatars[0]) || '??'}
        </div>
      </div>
    );
  }

  if (total >= 4) {
    const remaining = total - 3;
    return (
      <div className="w-[46px] h-[46px] relative flex-shrink-0">
        <div className="absolute top-0 left-0 w-[26px] h-[26px] bg-[#0068ff] rounded-full flex items-center justify-center text-[10px] font-medium text-white border-[1.5px] border-white z-10 overflow-hidden">
          {normalizeAvatarText(avatars[0]) || '??'}
        </div>
        <div className="absolute top-0 right-0 w-[26px] h-[26px] bg-[#0055d4] rounded-full flex items-center justify-center text-[10px] font-medium text-white border-[1.5px] border-white z-20 overflow-hidden">
          {normalizeAvatarText(avatars[1]) || '??'}
        </div>
        <div className="absolute bottom-0 left-0 w-[26px] h-[26px] bg-[#a3c9ff] rounded-full flex items-center justify-center text-[10px] font-medium text-white border-[1.5px] border-white z-20 overflow-hidden">
          {normalizeAvatarText(avatars[2]) || '??'}
        </div>
        <div className={`absolute bottom-0 right-0 w-[26px] h-[26px] rounded-full flex items-center justify-center text-[11px] font-semibold border-[1.5px] border-white z-30 overflow-hidden ${total === 4 ? 'bg-[#4a90e2] text-white' : 'bg-[#eaedf0] text-[#081c36]'
          }`}>
          {total === 4 ? (normalizeAvatarText(avatars[3]) || '??') : remaining}
        </div>
      </div>
    );
  }

  return (
    <div className="w-[46px] h-[46px] rounded-full bg-blue-100 flex items-center justify-center text-blue-600 font-semibold text-lg flex-shrink-0">
      {normalizeAvatarText(avatars[0]) || '??'}
    </div>
  );
};
