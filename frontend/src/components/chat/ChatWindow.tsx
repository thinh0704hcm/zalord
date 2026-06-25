import { useState, useEffect, useRef } from 'react';
import { UserPlus, Users, ThumbsUp } from 'lucide-react';
import { ZalordStickerIcon, ZalordPhotoIcon, ZalordAttachIcon, ZalordNamecardIcon, ZalordScreenshotIcon, ZalordTextFormatIcon, ZalordQuickMsgIcon, ZalordBankCardIcon, ZalordMoreIcon } from './ZalordIcons';
import AddMembersModal from './AddMembersModal';
import { Avatar } from './Avatar';

import type { Chat } from '../../pages/chat/ChatLayout';
import { messageService } from '../../services/message';
import { inboxService } from '../../services/inbox';
import { conversationService } from '../../services/conversation';
import { isMessageCreatedFrame, isMessageReadFrame, isTypingFrame, wsService } from '../../services/websocket';
import { userService } from '../../services/user';
import { groupService } from '../../services/group';

interface ChatWindowProps {
  chat?: Chat;
  onConversationReady?: (temporaryId: string | number, conversationId: string, lastMessage?: string) => void;
}

type UserMessage = {
  id?: string;
  text: string;
  time: string;
  dateKey: string;
  senderId?: string;
  senderName?: string;
  isSender?: boolean;
};

type SeenReader = {
  userId: string;
  displayName: string;
  avatarUrl: string | null;
  readAt: string;
};

type MessageHistoryItem = {
  messageId?: string;
  id?: string;
  content: string;
  createdAt: string;
  senderId: string;
};

const getMessageDateKey = (date: Date) => date.toISOString().slice(0, 10);
const TYPING_REFRESH_MS = 2500;
const TYPING_IDLE_MS = 3000;
const REMOTE_TYPING_TTL_MS = 5500;

const getAvatarText = (value: string) => {
  const words = value.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return `${words[0][0]}${words[words.length - 1][0]}`.toUpperCase();
};

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
  const [isAddMembersModalOpen, setIsAddMembersModalOpen] = useState(false);
  const [inputText, setInputText] = useState("");
  const [groupMemberCount, setGroupMemberCount] = useState<number | null>(null);
  const [groupMemberIds, setGroupMemberIds] = useState<string[]>([]);
  const [userMessages, setUserMessages] = useState<UserMessage[]>([]);
  const [lastMessageReaders, setLastMessageReaders] = useState<SeenReader[]>([]);
  const [typingUsers, setTypingUsers] = useState<Record<string, string>>({});
  const userMessagesRef = useRef<UserMessage[]>([]);
  const preservedMessagesRef = useRef<UserMessage[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const previousChatRef = useRef<Chat | undefined>(undefined);
  const senderNameCacheRef = useRef<Record<string, string>>({});
  const typingIdleTimeoutRef = useRef<number | null>(null);
  const remoteTypingTimeoutsRef = useRef<Record<string, number>>({});
  const lastTypingSentAtRef = useRef(0);
  const isTypingSentRef = useRef(false);

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

  const clearTypingUser = (userId: string) => {
    window.clearTimeout(remoteTypingTimeoutsRef.current[userId]);
    delete remoteTypingTimeoutsRef.current[userId];
    setTypingUsers(prev => {
      if (!prev[userId]) return prev;
      const next = { ...prev };
      delete next[userId];
      return next;
    });
  };

  const clearAllTypingUsers = () => {
    Object.values(remoteTypingTimeoutsRef.current).forEach(window.clearTimeout);
    remoteTypingTimeoutsRef.current = {};
    setTypingUsers({});
  };

  const clearOwnTypingTimers = () => {
    if (typingIdleTimeoutRef.current !== null) {
      window.clearTimeout(typingIdleTimeoutRef.current);
      typingIdleTimeoutRef.current = null;
    }
  };

  const sendTypingState = (isTyping: boolean, force = false) => {
    if (!chat || typeof chat.id !== 'string' || chat.isPending) return;
    const now = Date.now();

    if (isTyping && !force && isTypingSentRef.current && now - lastTypingSentAtRef.current < TYPING_REFRESH_MS) {
      return;
    }

    if (!isTyping && !isTypingSentRef.current && !force) {
      return;
    }

    wsService.sendTyping(chat.id, isTyping);
    lastTypingSentAtRef.current = now;
    isTypingSentRef.current = isTyping;
  };

  const scheduleTypingStop = () => {
    if (typingIdleTimeoutRef.current !== null) {
      window.clearTimeout(typingIdleTimeoutRef.current);
    }

    typingIdleTimeoutRef.current = window.setTimeout(() => {
      sendTypingState(false);
      clearOwnTypingTimers();
    }, TYPING_IDLE_MS);
  };

  const resolveSenderName = async (senderId?: string) => {
    if (!senderId) return '';
    if (senderId === currentUserId) return 'Bạn';
    if (!chat?.group) return chat?.name || '';
    if (senderNameCacheRef.current[senderId]) return senderNameCacheRef.current[senderId];

    try {
      const profile = await userService.findByUserId(senderId);
      senderNameCacheRef.current[senderId] = profile.displayName;
      return profile.displayName;
    } catch {
      return `Người dùng ${senderId.slice(0, 8)}`;
    }
  };

  const refreshLastMessageReaders = async (conversationId?: string) => {
    if (!conversationId || chat?.isPending) {
      setLastMessageReaders([]);
      return;
    }

    const readers = await messageService.lastReaders(conversationId);
    
    // Explicitly exclude both the current viewer and the sender of the last message
    // (Backend also excludes them, but this adds a bulletproof frontend guarantee)
    const msgs = userMessagesRef.current;
    const latestSenderId = msgs.length > 0 ? msgs[msgs.length - 1].senderId : null;
    
    const visibleReaders = readers.filter(reader => 
      reader.userId !== currentUserId && reader.userId !== latestSenderId
    );

    if (visibleReaders.length === 0) {
      setLastMessageReaders([]);
      return;
    }

    const profiles = await Promise.allSettled(
      visibleReaders.map(reader => userService.findByUserId(reader.userId))
    );

    setLastMessageReaders(visibleReaders.map((reader, index) => {
      const profile = profiles[index].status === 'fulfilled' ? profiles[index].value : null;

      return {
        userId: reader.userId,
        displayName: profile?.displayName ?? `Người dùng ${reader.userId.slice(0, 8)}`,
        avatarUrl: profile?.avatarUrl ?? null,
        readAt: reader.readAt,
      };
    }));
  };

  const markConversationRead = async (conversationId?: string, messageId?: string) => {
    if (!conversationId || chat?.isPending || !messageId) return;

    try {
      await inboxService.markRead(conversationId, messageId);
    } catch (error) {
      console.error('Failed to mark conversation as read:', error);
    }
  };

  useEffect(() => {
    if (!chat?.group || typeof chat.id !== 'string') {
      queueMicrotask(() => {
        setGroupMemberCount(null);
        setGroupMemberIds([]);
      });
      return;
    }

    if (chat.totalMembers) {
      queueMicrotask(() => setGroupMemberCount(chat.totalMembers ?? null));
    }

    let cancelled = false;
    groupService.get(chat.id)
      .then(group => {
        if (!cancelled) {
          setGroupMemberCount(group.members.length);
          setGroupMemberIds(group.members.map(member => member.userId));
        }
      })
      .catch(() => {
        if (!cancelled) {
          setGroupMemberIds([]);
          if (!chat.totalMembers) {
            setGroupMemberCount(null);
          }
        }
      });

    return () => {
      cancelled = true;
    };
  }, [chat?.group, chat?.id, chat?.totalMembers]);

  useEffect(() => {
    const previousChat = previousChatRef.current;
    
    if (chat?.id !== previousChat?.id) {
      const isSameTemporaryChatResolved = Boolean(
        previousChat?.pendingDirectUserId &&
        !chat?.pendingDirectUserId &&
        previousChat?.name === chat?.name
      );

      if (!isSameTemporaryChatResolved) {
        setUserMessages([]);
      }

      if (chat && typeof chat.id === 'string' && !chat.isPending) {
        const conversationId = chat.id;
        messageService.history(conversationId).then(async res => {
          const responseItems = (res.items || res.content || []) as MessageHistoryItem[];
          const historyMsgs: UserMessage[] = (await Promise.all(responseItems.map(async (m) => {
            const msgDate = new Date(m.createdAt);
            const timeStr = `${String(msgDate.getHours()).padStart(2, '0')}:${String(msgDate.getMinutes()).padStart(2, '0')}`;
            const isSender = currentUserId === m.senderId;
            return {
              id: m.messageId || m.id,
              text: m.content,
              time: timeStr,
              dateKey: getMessageDateKey(msgDate),
              senderId: m.senderId,
              senderName: isSender ? 'Bạn' : await resolveSenderName(m.senderId),
              isSender
            };
          }))).reverse();

          const latestMessageId = historyMsgs[historyMsgs.length - 1]?.id;
          void markConversationRead(conversationId, latestMessageId);
          void refreshLastMessageReaders(conversationId);

          setUserMessages(prev => {
            const preserved = isSameTemporaryChatResolved ? preservedMessagesRef.current : [];
            const merged = [...historyMsgs];

            [...preserved, ...prev].forEach(message => {
              if (message.id && merged.some(existing => existing.id === message.id)) return;
              if (!message.id && merged.some(existing => existing.text === message.text && existing.isSender === message.isSender)) return;
              merged.push(message);
            });

            return merged;
          });
          preservedMessagesRef.current = [];
          setTimeout(() => {
            messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
          }, 100);
        });
      } else {
        queueMicrotask(() => setLastMessageReaders([]));
      }
    }

    previousChatRef.current = chat;
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [chat, currentUserId]);

  useEffect(() => {
    const unsubscribe = wsService.onMessage(async (msg) => {
      if (isMessageCreatedFrame(msg) && msg.data?.conversationId === chat?.id) {
        const data = msg.data;
        if (data.senderId === currentUserId) return;

        const createdAt = data.createdAt ? new Date(data.createdAt) : new Date();
        const timeStr = `${String(createdAt.getHours()).padStart(2, '0')}:${String(createdAt.getMinutes()).padStart(2, '0')}`;
        const senderName = await resolveSenderName(data.senderId);
        clearTypingUser(data.senderId);
        setUserMessages(prev => {
          if (prev.some(existing => existing.id === data.messageId)) return prev;
          return [...prev, { 
            id: data.messageId,
            text: data.content, 
            time: timeStr,
            dateKey: getMessageDateKey(createdAt),
            senderId: data.senderId,
            senderName,
            isSender: false
          }];
        });
        void markConversationRead(data.conversationId, data.messageId)
          .then(() => refreshLastMessageReaders(data.conversationId));
      }

      if (isMessageReadFrame(msg) && msg.data.conversationId === chat?.id) {
        void refreshLastMessageReaders(msg.data.conversationId);
      }

      if (isTypingFrame(msg)) {
        const data = msg.data;
        const userId = data.userId;
        if (!userId || data.conversationId !== chat?.id || userId === currentUserId) return;

        if (!data.isTyping) {
          clearTypingUser(userId);
          return;
        }

        const senderName = await resolveSenderName(userId);
        setTypingUsers(prev => ({ ...prev, [userId]: senderName || chat?.name || 'Ai đó' }));
        window.clearTimeout(remoteTypingTimeoutsRef.current[userId]);
        remoteTypingTimeoutsRef.current[userId] = window.setTimeout(() => {
          clearTypingUser(userId);
        }, REMOTE_TYPING_TTL_MS);
      }
    });
    
    return unsubscribe;
  }, [chat?.id, currentUserId]);

  useEffect(() => {
    return () => {
      if (isTypingSentRef.current && chat && typeof chat.id === 'string' && !chat.isPending) {
        wsService.sendTyping(chat.id, false);
      }
      isTypingSentRef.current = false;
      lastTypingSentAtRef.current = 0;
      clearOwnTypingTimers();
      clearAllTypingUsers();
    };
  }, [chat?.id]);

  const handleKeyDown = async (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && inputText.trim() && chat) {
      const text = inputText.trim();
      setInputText("");
      sendTypingState(false, true);
      clearOwnTypingTimers();
      
      const now = new Date();
      const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
      const dateKey = getMessageDateKey(now);
      
      const optimisticId = `optimistic-${Date.now()}`;

      // Optimistic update
      setUserMessages(prev => [...prev, { id: optimisticId, text: text, time: timeStr, dateKey, senderId: currentUserId || undefined, senderName: 'Bạn', isSender: true }]);
      
      // Send via API. A searched user opens a temporary chat first; create the
      // real DIRECT conversation only when the first message is sent.
      try {
        let conversationId = String(chat.id);

        if (chat.pendingDirectUserId) {
          const conversation = await conversationService.createDirect(chat.pendingDirectUserId);
          conversationId = conversation.id;
        }

        const sentMessage = await messageService.send({
          conversationId,
          content: text
        });

        setUserMessages(prev => {
          const next = prev.map(message => message.id === optimisticId
            ? {
                ...message,
                id: sentMessage?.messageId || sentMessage?.id || optimisticId,
                time: sentMessage?.createdAt
                  ? `${String(new Date(sentMessage.createdAt).getHours()).padStart(2, '0')}:${String(new Date(sentMessage.createdAt).getMinutes()).padStart(2, '0')}`
                  : message.time,
              }
            : message
          );
          preservedMessagesRef.current = next;
          return next;
        });

        void refreshLastMessageReaders(conversationId);

        if (chat.pendingDirectUserId) {
          onConversationReady?.(chat.id, conversationId, text);
        }
      } catch (error) {
        console.error('Failed to send message', error);
      }
    }
  };


  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setInputText(value);

    if (!value.trim()) {
      sendTypingState(false);
      clearOwnTypingTimers();
      return;
    }

    sendTypingState(true);
    scheduleTypingStop();
  };

  const typingNames = Object.values(typingUsers);
  const typingText = typingNames.length === 0
    ? ''
    : typingNames.length === 1
      ? `${typingNames[0]} đang soạn tin nhắn`
      : `${typingNames.length} người đang soạn tin nhắn`;

  const handleAddMembers = async (memberIds: string[]) => {
    if (!chat?.group || typeof chat.id !== 'string') return;

    let updatedGroup = null;
    for (const memberId of memberIds) {
      updatedGroup = await groupService.addMember(chat.id, memberId);
    }

    if (updatedGroup) {
      setGroupMemberCount(updatedGroup.members.length);
      setGroupMemberIds(updatedGroup.members.map(member => member.userId));
    }
    setIsAddMembersModalOpen(false);
  };

  if (!chat) {
    return <div className="flex-1 h-screen flex flex-col bg-[#eef0f1] relative items-center justify-center text-gray-500">Chọn một đoạn chat</div>;
  }

  return (
    <div className="flex-1 h-screen flex flex-col bg-[#eef0f1] relative">
      {/* Header */}
      <div className="h-[64px] bg-white border-b border-[#d6dbe1] flex items-center justify-between px-4 flex-shrink-0">
        <div className="flex items-center gap-3">
          <Avatar url={chat.avatarUrl} name={chat.name} className="w-[42px] h-[42px] text-[15px]" />
          <div className="flex flex-col justify-center">
            <h2 className="font-semibold text-gray-900 text-[16px] leading-tight">{chat.name}</h2>
            <div className="flex items-center text-[13px] text-gray-500 mt-0.5">
              {chat.group ? <Users size={13} className="mr-1" /> : <UserPlus size={13} className="mr-1" />}
              {!chat.group && (
                <span className={`mr-1.5 h-2 w-2 rounded-full ${chat.presenceStatus === 'online' ? 'bg-[#31a24c]' : 'bg-[#b8c0cc]'}`} />
              )}
              <span>{chat.group ? `${groupMemberCount ?? chat.totalMembers ?? 0} thành viên` : (chat.presenceStatus === 'online' ? 'Trực tuyến' : 'Ngoại tuyến')}</span>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-1 text-gray-600">
          {chat.group && (
            <button
              onClick={() => setIsAddMembersModalOpen(true)}
              className="p-1.5 rounded-md transition-colors hover:bg-gray-100"
              title="Thêm thành viên"
            >
              <UserPlus size={18} />
            </button>
          )}
        </div>
      </div>

      {/* Messages Area */}
      <div className="flex-1 overflow-y-auto px-4 py-2 pb-3 flex flex-col gap-3">
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
          {userMessages.map((msg, index) => {
            const isSender = msg.isSender !== false;
            const previousMessage = userMessages[index - 1];
            const isIncoming = !isSender;
            const isFirstMessageInIncomingGroup = Boolean(
              isIncoming &&
              (!previousMessage ||
                previousMessage.isSender !== false ||
                previousMessage.dateKey !== msg.dateKey ||
                previousMessage.senderId !== msg.senderId)
            );
            const shouldShowIncomingAvatar = Boolean(chat && isIncoming && isFirstMessageInIncomingGroup);
            const shouldReserveIncomingAvatarSpace = Boolean(chat && isIncoming);
            const avatarText = chat?.group ? (msg.senderName || '') : (typeof chat?.avatar === 'string' ? chat.avatar : getAvatarText(chat?.name || ''));

            if (isSender) {
              return (
                <div key={index} className="flex w-full justify-end">
                  <div className="max-w-[70%] bg-[#e5efff] border-[#cce1ff] rounded-lg px-3 py-2 shadow-sm border">
                    <div className="text-[#081c36] text-[15px]">{msg.text}</div>
                    <div className="text-[#7589A3] text-[12px] mt-1 text-right">{msg.time}</div>
                  </div>
                </div>
              );
            }

            return (
              <div key={index} className="flex w-full justify-start items-start gap-2.5">
                {shouldReserveIncomingAvatarSpace && (
                  shouldShowIncomingAvatar ? (
                    <Avatar 
                      url={chat?.group ? undefined : chat?.avatarUrl} // For group we might not have the sender's avatar URL yet unless we fetch it. We'll pass undefined for group sender for now.
                      name={avatarText} 
                      className="w-10 h-10 text-[14px]" 
                    />
                  ) : (
                    <div className="w-10 flex-shrink-0" />
                  )
                )}
                <div className="max-w-[70%] bg-white border-[#e5e7eb] rounded-lg px-3 py-2 shadow-sm border">
                  {chat?.group && isFirstMessageInIncomingGroup && msg.senderName && (
                    <div className="text-[#7589A3] text-[12px] font-medium mb-1">{msg.senderName}</div>
                  )}
                  <div className="text-[#081c36] text-[15px]">{msg.text}</div>
                  <div className="text-[#7589A3] text-[12px] mt-1 text-right">{msg.time}</div>
                </div>
              </div>
            );
          })}
          {typingText && (
            <div className="flex w-full justify-start items-start ml-2 mb-2 mt-2">
              <div className="bg-white border border-[#e5e7eb] rounded-lg px-3 py-2 shadow-sm flex items-center gap-2 text-[#7589A3] text-[13px]">
                <span className="flex items-center gap-1" aria-hidden="true">
                  <span className="w-1.5 h-1.5 rounded-full bg-[#7589A3] animate-bounce" />
                  <span className="w-1.5 h-1.5 rounded-full bg-[#7589A3] animate-bounce [animation-delay:120ms]" />
                  <span className="w-1.5 h-1.5 rounded-full bg-[#7589A3] animate-bounce [animation-delay:240ms]" />
                </span>
                <span>{typingText}</span>
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />

          {lastMessageReaders.length > 0 && (
            <div className="mt-1 ml-auto flex -space-x-2 pr-1">
              {lastMessageReaders.slice(0, 5).map(reader => (
                <Avatar
                  key={reader.userId}
                  url={reader.avatarUrl}
                  name={reader.displayName}
                  title={`${reader.displayName} đã xem`}
                  className="h-6 w-6 border-2 border-[#eef0f1] text-[10px] shadow-sm"
                />
              ))}
              {lastMessageReaders.length > 5 && (
                <div 
                  className="flex h-6 min-w-6 items-center justify-center rounded-full border-2 border-[#eef0f1] bg-white px-1 text-[10px] font-semibold text-[#081c36] shadow-sm cursor-default"
                  title={`${lastMessageReaders.slice(5).map(r => r.displayName).join('\n')}`}
                >
                  +{lastMessageReaders.length - 5}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      <AddMembersModal
        isOpen={isAddMembersModalOpen}
        onClose={() => setIsAddMembersModalOpen(false)}
        onConfirm={handleAddMembers}
        existingMemberIds={groupMemberIds}
      />

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
          <input 
            type="text" 
            placeholder={`Nhập @, tin nhắn tới ${chat.name}`}
            value={inputText}
            onChange={handleInputChange}
            onKeyDown={handleKeyDown}
            className="flex-1 bg-transparent border-none outline-none text-[15px] py-1 text-gray-800 placeholder-gray-500"
          />
        </div>
      </div>
    </div>
  );
}
