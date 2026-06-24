import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import SidebarNav from '../../components/chat/SidebarNav';
import ChatList from '../../components/chat/ChatList';
import ChatWindow from '../../components/chat/ChatWindow';
import { wsService } from '../../services/websocket';

import { userService, type UserProfile } from '../../services/user';
import { inboxService, type InboxItemResponse } from '../../services/inbox';

export interface Chat {
  id: string | number;
  name: string;
  message: string;
  time: string;
  unread: number;
  avatar: string | string[];
  totalMembers?: number;
  group?: boolean;
  pendingDirectUserId?: string;
  isPending?: boolean;
}

const relativeTime = (value: string | null) => {
  if (!value) return '';

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';

  const diffMs = Date.now() - date.getTime();
  const diffMinutes = Math.max(0, Math.floor(diffMs / 60000));

  if (diffMinutes < 1) return 'Vừa xong';
  if (diffMinutes < 60) return `${diffMinutes} phút`;

  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours} giờ`;

  const diffDays = Math.floor(diffHours / 24);
  if (diffDays === 1) return 'Hôm qua';
  if (diffDays < 7) return `${diffDays} ngày`;

  return date.toLocaleDateString('vi-VN');
};

const fallbackUserLabel = (userId: string | null) => {
  if (!userId) return 'Cuộc trò chuyện';
  return `Người dùng ${userId.slice(0, 8)}`;
};

const inboxItemToChat = async (item: InboxItemResponse): Promise<Chat> => {
  let name = fallbackUserLabel(item.otherUserId);
  let avatar: string | string[] = name.charAt(0).toUpperCase();

  if (item.otherUserId) {
    try {
      const profile = await userService.findByUserId(item.otherUserId);
      name = profile.displayName;
      avatar = profile.avatarUrl || profile.displayName.charAt(0).toUpperCase();
    } catch (error) {
      console.warn('Failed to load profile for inbox item', item.otherUserId, error);
    }
  }

  return {
    id: item.conversationId,
    name,
    message: item.lastMessagePreview || 'Bắt đầu trò chuyện',
    time: relativeTime(item.lastMessageAt),
    unread: item.unreadCount || 0,
    avatar
  };
};

export default function ChatLayout() {
  const navigate = useNavigate();
  const [chats, setChats] = useState<Chat[]>([]);
  const [activeChatId, setActiveChatId] = useState<string | number | null>(null);
  const [isLoadingChats, setIsLoadingChats] = useState(true);
  const [chatListError, setChatListError] = useState('');
  const activeChat = chats.find(c => c.id === activeChatId);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/account/login');
      return;
    }

    let cancelled = false;

    const loadInbox = async () => {
      setIsLoadingChats(true);
      setChatListError('');

      try {
        const page = await inboxService.list();
        if (cancelled) return;

        const fetchedChats = await Promise.all(page.items.map(inboxItemToChat));
        setChats(prev => {
          const pendingChats = prev.filter(chat => chat.isPending);
          return [...pendingChats, ...fetchedChats];
        });
        setActiveChatId(current => current ?? fetchedChats[0]?.id ?? null);
      } catch (error: any) {
        if (!cancelled) {
          setChatListError(error.message || 'Không thể tải danh sách chat');
        }
      } finally {
        if (!cancelled) {
          setIsLoadingChats(false);
        }
      }
    };

    const handleAuthExpired = () => {
      navigate('/account/login');
    };
    window.addEventListener('auth-expired', handleAuthExpired);

    wsService.connect(token);
    loadInbox();
    
    const unsubscribeWs = wsService.onMessage((msg) => {
      if (msg.type === 'message.created') {
        const { conversationId, content, createdAt } = msg.data;
        
        setChats(prevChats => {
          const existingChatIndex = prevChats.findIndex(c => c.id === conversationId);
          if (existingChatIndex !== -1) {
            const updatedChats = [...prevChats];
            const chat = updatedChats[existingChatIndex];
            
            // Increment unread if it's not the active chat.
            // Since we don't have activeChatId in dependencies (to avoid reconnecting),
            // we use a functional update approach if possible, but actually we can just
            // rely on the user reading it later. For now, activeChatId from closure might be stale.
            // To fix stale activeChatId, we can use setState callback safely:
            setChats(latestChats => {
              const idx = latestChats.findIndex(c => c.id === conversationId);
              if (idx !== -1) {
                const copy = [...latestChats];
                const c = copy[idx];
                // In setChats callback, we don't have the current activeChatId easily.
                // We'll increment unread, and when the user clicks it, it resets.
                copy[idx] = {
                  ...c,
                  message: content,
                  time: relativeTime(createdAt),
                  unread: c.unread + 1
                };
                const [moved] = copy.splice(idx, 1);
                return [moved, ...copy];
              }
              return latestChats;
            });
            return prevChats; // hand off to the inner setChats
          } else {
            // New conversation
            loadInbox();
            return prevChats;
          }
        });
      }
    });

    return () => {
      cancelled = true;
      window.removeEventListener('auth-expired', handleAuthExpired);
      unsubscribeWs();
      wsService.disconnect();
    };
  }, [navigate]);

  const handleCreateGroup = (groupName: string, selectedAvatars: string[], totalMembers: number) => {
    const newChat: Chat = {
      id: Date.now(),
      name: groupName,
      message: 'Bạn vừa tạo nhóm',
      time: 'Vừa xong',
      unread: 0,
      avatar: selectedAvatars,
      totalMembers,
      group: true
    };
    setChats([newChat, ...chats]);
    setActiveChatId(newChat.id);
  };

  const handleStartDirectChat = (user: UserProfile) => {
    const pendingId = `pending-direct-${user.userId}`;
    const existingChat = chats.find(c => c.id === pendingId || c.pendingDirectUserId === user.userId);

    if (existingChat) {
      setActiveChatId(existingChat.id);
      return;
    }

    const newChat: Chat = {
      id: pendingId,
      name: user.displayName,
      message: 'Bắt đầu trò chuyện',
      time: 'Vừa xong',
      unread: 0,
      avatar: user.avatarUrl || user.displayName.charAt(0).toUpperCase(),
      pendingDirectUserId: user.userId,
      isPending: true
    };

    setChats(prev => [newChat, ...prev]);
    setActiveChatId(newChat.id);
  };

  const handleConversationReady = (temporaryId: string | number, conversationId: string) => {
    setChats(prev => {
      const existingRealChat = prev.find(chat => chat.id === conversationId);
      if (existingRealChat) {
        return prev.filter(chat => chat.id !== temporaryId);
      }

      return prev.map(chat => chat.id === temporaryId
        ? { ...chat, id: conversationId, isPending: false, pendingDirectUserId: undefined }
        : chat
      );
    });
    setActiveChatId(conversationId);
  };

  return (
    <div className="flex h-screen w-full overflow-hidden bg-white">
      <SidebarNav />
      <ChatList 
        chats={chats} 
        activeChatId={activeChatId ?? ''} 
        onSelectChat={setActiveChatId} 
        onCreateGroup={handleCreateGroup} 
        onStartDirectChat={handleStartDirectChat}
        isLoading={isLoadingChats}
        error={chatListError}
      />
      <ChatWindow chat={activeChat} onConversationReady={handleConversationReady} />
    </div>
  );
}
