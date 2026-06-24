import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import SidebarNav from '../../components/chat/SidebarNav';
import ChatList from '../../components/chat/ChatList';
import ChatWindow from '../../components/chat/ChatWindow';
import { wsService } from '../../services/websocket';

import { userService, type UserProfile } from '../../services/user';
import { inboxService, type InboxItemResponse } from '../../services/inbox';
import { groupService } from '../../services/group';

export interface Chat {
  id: string | number;
  name: string;
  message: string;
  time: string;
  lastMessageAt?: string | null;
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

const getInitials = (name: string) => {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return `${words[0][0]}${words[words.length - 1][0]}`.toUpperCase();
};

const fallbackUserLabel = (userId: string | null) => {
  if (!userId) return 'Cuộc trò chuyện';
  return `Người dùng ${userId.slice(0, 8)}`;
};

const getCurrentUserId = () => {
  try {
    const token = localStorage.getItem('token');
    if (!token) return null;
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.sub as string;
  } catch {
    return null;
  }
};

const formatLastMessagePreview = (preview: string | null, lastSenderId: string | null, currentUserId: string | null, otherUserName: string) => {
  if (!preview) return 'Bắt đầu trò chuyện';
  if (!lastSenderId || !currentUserId) return preview;

  return lastSenderId === currentUserId
    ? `Bạn: ${preview}`
    : `${otherUserName}: ${preview}`;
};

const inboxItemToChat = async (item: InboxItemResponse, currentUserId: string | null): Promise<Chat> => {
  if (!item.otherUserId) {
    try {
      const group = await groupService.get(item.conversationId);
      const memberProfiles = await Promise.allSettled(
        group.members.map(member => userService.findByUserId(member.userId))
      );
      const memberAvatars = memberProfiles
        .map(result => result.status === 'fulfilled' ? getInitials(result.value.displayName) : '')
        .filter(Boolean);

      return {
        id: item.conversationId,
        name: group.name,
        message: formatLastMessagePreview(item.lastMessagePreview, item.lastSenderId, currentUserId, group.name),
        time: relativeTime(item.lastMessageAt),
        lastMessageAt: item.lastMessageAt,
        unread: item.unreadCount || 0,
        avatar: memberAvatars.length > 0 ? memberAvatars : [getInitials(group.name)],
        totalMembers: group.members.length,
        group: true
      };
    } catch (error) {
      console.warn('Failed to load group for inbox item', item.conversationId, error);
      return {
        id: item.conversationId,
        name: 'Nhóm chat',
        message: item.lastMessagePreview || 'Bắt đầu trò chuyện',
        time: relativeTime(item.lastMessageAt),
        lastMessageAt: item.lastMessageAt,
        unread: item.unreadCount || 0,
        avatar: ['NC', '??', '??'],
        totalMembers: 3,
        group: true
      };
    }
  }

  let name = fallbackUserLabel(item.otherUserId);
  let avatar: string | string[] = getInitials(name);

  try {
    const profile = await userService.findByUserId(item.otherUserId);
    name = profile.displayName;
    avatar = getInitials(profile.displayName);
  } catch (error) {
    console.warn('Failed to load profile for inbox item', item.otherUserId, error);
  }

  return {
    id: item.conversationId,
    name,
    message: formatLastMessagePreview(item.lastMessagePreview, item.lastSenderId, currentUserId, name),
    time: relativeTime(item.lastMessageAt),
    lastMessageAt: item.lastMessageAt,
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
  const activeChatIdRef = useRef<string | number | null>(null);
  const activeChat = chats.find(c => c.id === activeChatId);

  useEffect(() => {
    const ticker = setInterval(() => {
      setChats(prev => prev.map(chat =>
        chat.lastMessageAt
          ? { ...chat, time: relativeTime(chat.lastMessageAt) }
          : chat
      ));
    }, 60_000);
    return () => clearInterval(ticker);
  }, []);

  useEffect(() => {
    activeChatIdRef.current = activeChatId;
  }, [activeChatId]);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/account/login');
      return;
    }

    let cancelled = false;

    const loadInbox = async (showLoading = true) => {
      if (showLoading) {
        setIsLoadingChats(true);
      }
      setChatListError('');

      try {
        const page = await inboxService.list();
        if (cancelled) return;

        const currentUserId = getCurrentUserId();
        const fetchedChats = await Promise.all(page.items.map(item => inboxItemToChat(item, currentUserId)));
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
        if (!cancelled && showLoading) {
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
    
    const refreshInboxAfterNewMessage = () => {
      loadInbox(false);
      window.setTimeout(() => {
        if (!cancelled) loadInbox(false);
      }, 350);
      window.setTimeout(() => {
        if (!cancelled) loadInbox(false);
      }, 1200);
    };

    const unsubscribeWs = wsService.onMessage((msg) => {
      if (msg.type !== 'message.created') return;

      const { conversationId, content, createdAt, senderId } = msg.data;
      if (!conversationId) return;

      let foundConversation = false;

      setChats(prevChats => {
        const existingChatIndex = prevChats.findIndex(c => c.id === conversationId);
        if (existingChatIndex === -1) return prevChats;

        foundConversation = true;
        const updatedChats = [...prevChats];
        const chat = updatedChats[existingChatIndex];
        const currentUserId = getCurrentUserId();
        const isCurrentUserSender = senderId === currentUserId;

        updatedChats[existingChatIndex] = {
          ...chat,
          message: isCurrentUserSender ? `Bạn: ${content || '[Tin nhắn]'}` : `${chat.name}: ${content || '[Tin nhắn]'}`,
          time: relativeTime(createdAt),
          lastMessageAt: createdAt,
          unread: activeChatIdRef.current === conversationId || isCurrentUserSender ? chat.unread : chat.unread + 1
        };

        const [moved] = updatedChats.splice(existingChatIndex, 1);
        return [moved, ...updatedChats];
      });

      if (!foundConversation) {
        refreshInboxAfterNewMessage();
      }
    });

    return () => {
      cancelled = true;
      window.removeEventListener('auth-expired', handleAuthExpired);
      unsubscribeWs();
      wsService.disconnect();
    };
  }, [navigate]);

  const handleCreateGroup = async (groupName: string, memberIds: string[], selectedAvatars: string[], totalMembers: number) => {
    const group = await groupService.create({
      name: groupName,
      avatarUrl: null,
      memberIds
    });

    const newChat: Chat = {
      id: group.id,
      name: group.name,
      message: 'Bạn vừa tạo nhóm',
      time: 'Vừa xong',
      unread: 0,
      avatar: selectedAvatars,
      totalMembers: group.members?.length || totalMembers,
      group: true
    };
    setChats(prev => [newChat, ...prev]);
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
      avatar: getInitials(user.displayName),
      pendingDirectUserId: user.userId,
      isPending: true
    };

    setChats(prev => [newChat, ...prev]);
    setActiveChatId(newChat.id);
  };

  const handleConversationReady = (temporaryId: string | number, conversationId: string, lastMessage?: string) => {
    setChats(prev => {
      const existingRealChat = prev.find(chat => chat.id === conversationId);
      if (existingRealChat) {
        return prev.filter(chat => chat.id !== temporaryId);
      }

      return prev.map(chat => chat.id === temporaryId
        ? {
            ...chat,
            id: conversationId,
            message: lastMessage ? `Bạn: ${lastMessage}` : chat.message,
            time: 'Vừa xong',
            isPending: false,
            pendingDirectUserId: undefined
          }
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
