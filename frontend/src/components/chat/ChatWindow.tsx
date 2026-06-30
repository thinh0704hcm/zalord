import { useState, useEffect, useRef, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { UserPlus, Users, X, RotateCcw, Folder, Download } from 'lucide-react';
import { ZalordStickerIcon, ZalordPhotoIcon, ZalordAttachIcon, ZalordNamecardIcon, ZalordScreenshotIcon, ZalordTextFormatIcon, ZalordQuickMsgIcon, ZalordBankCardIcon, ZalordMoreIcon, ZalordReplyIcon } from './ZalordIcons';
import AddMembersModal from './AddMembersModal';
import GroupSidebar from './GroupSidebar';
import { Avatar } from './Avatar';

import type { Chat } from '../../pages/chat/ChatLayout';
import { messageService } from '../../services/message';
import { inboxService } from '../../services/inbox';
import { conversationService } from '../../services/conversation';
import { isMessageCreatedFrame, isMessageReadFrame, isTypingFrame, isMessageRecalledFrame, isGroupMemberEventFrame, wsService } from '../../services/websocket';
import { userService } from '../../services/user';
import { mediaService, type MediaResponse } from '../../services/media';
import api from '../../services/api';
import { groupService } from '../../services/group';

interface ChatWindowProps {
  chat?: Chat;
  onConversationReady?: (temporaryId: string | number, conversationId: string, lastMessage?: string) => void;
}

type ReplyToSnippet = {
  messageId: string;
  senderId: string;
  preview: string;
};

type UserMessage = {
  id?: string;
  text: string;
  time: string;
  dateKey: string;
  senderId?: string;
  senderName?: string;
  isSender?: boolean;
  replyTo?: ReplyToSnippet | null;
  isRecalled?: boolean;
  attachmentIds?: string[];
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
  replyTo?: ReplyToSnippet | null;
  recalledAt?: string;
  attachmentIds?: string[];
};

const getMessageDateKey = (date: Date) => date.toISOString().slice(0, 10);
const TYPING_REFRESH_MS = 2500;
const TYPING_IDLE_MS = 3000;
const REMOTE_TYPING_TTL_MS = 5500;
const HISTORY_PAGE_SIZE = 50;
const HISTORY_TOP_LOAD_THRESHOLD_PX = 96;

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

function ImagePreviewModal({ url, onClose }: { url: string, onClose: () => void }) {
  useEffect(() => {
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = 'unset'; };
  }, []);

  return createPortal(
    <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/80" onClick={onClose}>
      <button 
        className="absolute top-4 right-4 text-white hover:bg-white/10 rounded-full p-2 transition-colors"
        onClick={onClose}
        title="Đóng (Esc)"
      >
        <X size={24} />
      </button>
      <img 
        src={url} 
        className="max-w-[90vw] max-h-[90vh] object-contain rounded shadow-2xl" 
        onClick={(e) => e.stopPropagation()} 
        alt="Preview"
      />
    </div>,
    document.body
  );
}

function AttachmentItem({ id }: { id: string }) {
  const [media, setMedia] = useState<MediaResponse | null>(null);
  const [hasError, setHasError] = useState(false);
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);
  
  useEffect(() => {
    let mounted = true;
    const fetchMedia = async () => {
      try {
        const response = await api.get(`/media/${id}`);
        if (mounted) setMedia(response.data.data);
      } catch (e) {
        console.error('Failed to fetch media metadata', e);
        if (mounted) setHasError(true);
      }
    };
    fetchMedia();
    return () => { mounted = false; };
  }, [id]);

  if (hasError) return <div className="text-xs text-red-400 italic p-1 border border-red-200 bg-red-50 rounded mt-1 max-w-[200px]">Tệp không tồn tại hoặc đã bị xóa</div>;
  if (!media) return <div className="text-xs text-gray-500 italic p-1">Đang tải tệp đính kèm...</div>;

  const isImage = media.mimeType?.startsWith('image/');
  
  if (isImage && media.url) {
    return (
      <div 
        className="relative max-w-sm rounded-lg overflow-hidden border border-gray-200 cursor-pointer group"
      >
        <img 
          src={media.url} 
          alt={media.fileName || 'Image'} 
          className="max-w-full h-auto object-cover max-h-64" 
          onClick={() => setIsPreviewOpen(true)}
          onLoad={() => {
            const container = document.getElementById('chat-scroll-container');
            if (container) {
              const { scrollTop, scrollHeight, clientHeight } = container;
              if (scrollHeight - scrollTop - clientHeight < 1000) {
                container.scrollTo({ top: scrollHeight, behavior: 'auto' });
              }
            }
          }}
        />
        <button 
          onClick={(e) => {
            e.stopPropagation();
            if (media.url) {
              const a = document.createElement('a');
              a.href = media.url;
              a.download = media.fileName || media.id;
              a.target = '_blank';
              document.body.appendChild(a);
              a.click();
              document.body.removeChild(a);
            }
          }}
          className="absolute top-2 right-2 w-8 h-8 bg-black/50 hover:bg-black/70 text-white rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
          title="Tải xuống"
        >
          <Download size={16} />
        </button>
        {isPreviewOpen && <ImagePreviewModal url={media.url} onClose={() => setIsPreviewOpen(false)} />}
      </div>
    );
  }

  return (
    <div 
      className="flex items-center gap-3 p-3 bg-white border border-gray-200 rounded-lg max-w-sm cursor-pointer hover:bg-gray-50 transition-colors group"
      onClick={() => {
        if (media.url) {
          const a = document.createElement('a');
          a.href = media.url;
          a.download = media.fileName || media.id;
          a.target = '_blank';
          document.body.appendChild(a);
          a.click();
          document.body.removeChild(a);
        }
      }}
    >
      <div className="w-8 h-8 flex-shrink-0 flex items-center justify-center bg-blue-50 rounded text-blue-600">
        <ZalordAttachIcon className="w-5 h-5" />
      </div>
      <div className="flex flex-col min-w-0 flex-1">
        <span className="text-sm font-medium text-gray-800 truncate">{media.fileName || media.id}</span>
        {media.sizeBytes && <span className="text-xs text-gray-500">{(media.sizeBytes / 1024).toFixed(1)} KB</span>}
      </div>
      <div className="w-8 h-8 flex items-center justify-center text-gray-400 group-hover:text-blue-500 transition-colors flex-shrink-0">
        <Download size={18} />
      </div>
    </div>
  );
}

function PendingAttachmentItem({ file, onRemove }: { file: File, onRemove: () => void }) {
  const isImage = file.type.startsWith('image/');
  const [preview, setPreview] = useState<string | null>(null);
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);

  useEffect(() => {
    if (isImage) {
      const url = URL.createObjectURL(file);
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setPreview(url);
      return () => URL.revokeObjectURL(url);
    }
  }, [file, isImage]);

  return (
    <div className="relative group">
      {isImage && preview ? (
        <>
          <div 
            className="w-16 h-16 rounded overflow-hidden border border-gray-200 cursor-pointer"
            onClick={() => setIsPreviewOpen(true)}
          >
            <img src={preview} alt={file.name} className="w-full h-full object-cover" />
          </div>
          {isPreviewOpen && <ImagePreviewModal url={preview} onClose={() => setIsPreviewOpen(false)} />}
        </>
      ) : (
        <div className="bg-gray-100 rounded px-3 py-1.5 flex items-center gap-2">
          <ZalordAttachIcon className="w-4 h-4 text-gray-500" />
          <span className="text-sm text-gray-700 truncate max-w-[150px]">{file.name}</span>
        </div>
      )}
      <button
        onClick={onRemove}
        className="absolute -top-2 -right-2 bg-white rounded-full p-0.5 shadow border border-gray-200 text-gray-500 hover:text-red-500 opacity-0 group-hover:opacity-100 transition-opacity"
      >
        <X size={14} />
      </button>
    </div>
  );
}

function MediaSidebar({ conversationId, onClose }: { conversationId: string, onClose: () => void }) {
  const [media, setMedia] = useState<MediaResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setIsLoading(true);
    mediaService.listByConversation(conversationId)
      .then(res => {
        if (mounted) {
          setMedia(res);
          setIsLoading(false);
        }
      })
      .catch(err => {
        console.error(err);
        if (mounted) setIsLoading(false);
      });
    return () => { mounted = false; };
  }, [conversationId]);

  const images = media.filter(m => m.mimeType?.startsWith('image/'));
  const files = media.filter(m => !m.mimeType?.startsWith('image/'));

  return (
    <div className="w-[300px] bg-white border-l border-[#eaedf0] flex flex-col h-full flex-shrink-0">
      <div className="h-[64px] border-b border-[#eaedf0] flex items-center justify-between px-4">
        <h3 className="font-semibold text-[#001a33]">Kho lưu trữ</h3>
        <button onClick={onClose} className="p-1 hover:bg-gray-100 rounded-full text-gray-500">
          <X size={20} />
        </button>
      </div>
      <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-6">
        {isLoading ? (
          <div className="text-center text-sm text-gray-500 mt-4">Đang tải...</div>
        ) : (
          <>
            <div>
              <h4 className="font-medium text-gray-800 mb-3 text-sm flex items-center gap-2">
                <ZalordPhotoIcon className="w-4 h-4" /> Hình ảnh ({images.length})
              </h4>
              {images.length > 0 ? (
                <div className="grid grid-cols-3 gap-2">
                  {images.map(img => (
                    <div 
                      key={img.id} 
                      className="aspect-square bg-gray-100 rounded overflow-hidden cursor-pointer hover:opacity-90 border border-gray-200"
                      onClick={() => img.url && setPreviewUrl(img.url)}
                      title={img.fileName || img.id}
                    >
                      {img.url ? <img src={img.url} className="w-full h-full object-cover" alt="" /> : <div className="w-full h-full flex items-center justify-center text-xs text-gray-400">Lỗi</div>}
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-xs text-gray-500 text-center py-4 bg-gray-50 rounded">Chưa có hình ảnh nào</div>
              )}
            </div>

            <div>
              <h4 className="font-medium text-gray-800 mb-3 text-sm flex items-center gap-2">
                <ZalordAttachIcon className="w-4 h-4" /> Tài liệu ({files.length})
              </h4>
              {files.length > 0 ? (
                <div className="grid grid-cols-2 gap-2">
                  {files.map(file => (
                    <div 
                      key={file.id} 
                      className="flex flex-col items-center gap-1 p-2 bg-white border border-gray-200 rounded cursor-pointer hover:bg-gray-50 group text-center relative"
                      onClick={() => {
                        if (file.url) {
                          const a = document.createElement('a');
                          a.href = file.url;
                          a.download = file.fileName || file.id;
                          a.target = '_blank';
                          document.body.appendChild(a);
                          a.click();
                          document.body.removeChild(a);
                        }
                      }}
                    >
                      <div className="w-10 h-10 bg-blue-50 text-blue-600 rounded-full flex items-center justify-center flex-shrink-0">
                        <ZalordAttachIcon className="w-5 h-5" />
                      </div>
                      <div className="flex flex-col min-w-0 w-full px-1">
                        <span className="text-[13px] font-medium text-gray-700 truncate" title={file.fileName || file.id}>{file.fileName || file.id}</span>
                        {file.sizeBytes && <span className="text-[11px] text-gray-500">{(file.sizeBytes / 1024).toFixed(1)} KB</span>}
                      </div>
                      <div className="absolute top-1 right-1 w-6 h-6 flex items-center justify-center text-gray-300 group-hover:text-blue-500 transition-colors">
                        <Download size={14} />
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-xs text-gray-500 text-center py-4 bg-gray-50 rounded">Chưa có tài liệu nào</div>
              )}
            </div>
          </>
        )}
      </div>
      {previewUrl && <ImagePreviewModal url={previewUrl} onClose={() => setPreviewUrl(null)} />}
    </div>
  );
}

export default function ChatWindow({ chat, onConversationReady }: ChatWindowProps) {
  const [isAddMembersModalOpen, setIsAddMembersModalOpen] = useState(false);
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const [isMediaSidebarOpen, setIsMediaSidebarOpen] = useState(false);
  const [isKicked, setIsKicked] = useState(false);
  const [inputText, setInputText] = useState("");
  const [groupMemberCount, setGroupMemberCount] = useState<number | null>(null);
  const [groupMemberIds, setGroupMemberIds] = useState<string[]>([]);
  const [userMessages, setUserMessages] = useState<UserMessage[]>([]);
  const [lastMessageReaders, setLastMessageReaders] = useState<SeenReader[]>([]);
  const [typingUsers, setTypingUsers] = useState<Record<string, string>>({});
  const userMessagesRef = useRef<UserMessage[]>([]);
  const preservedMessagesRef = useRef<UserMessage[]>([]);
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const oldestMessageCursorRef = useRef<string | null>(null);
  const [hasMoreMessages, setHasMoreMessages] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const isLoadingMoreRef = useRef(false);
  const isPrependingHistoryRef = useRef(false);
  const previousChatRef = useRef<Chat | undefined>(undefined);
  const [pendingAttachments, setPendingAttachments] = useState<File[]>([]);
  const isAutoScrollingRef = useRef(false);
  const lastScrollHeightRef = useRef(0);

  const scrollToBottom = useCallback((smooth = false) => {
    if (scrollContainerRef.current) {
      isAutoScrollingRef.current = true;
      scrollContainerRef.current.scrollTo({
        top: scrollContainerRef.current.scrollHeight,
        behavior: smooth ? 'smooth' : 'auto'
      });
      setTimeout(() => {
        isAutoScrollingRef.current = false;
      }, 500);
    }
  }, []);
  const [isUploading, setIsUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const senderNameCacheRef = useRef<Record<string, string>>({});
  const typingIdleTimeoutRef = useRef<number | null>(null);
  const remoteTypingTimeoutsRef = useRef<Record<string, number>>({});
  const lastTypingSentAtRef = useRef(0);
  const isTypingSentRef = useRef(false);

  const [replyingTo, setReplyingTo] = useState<{ id: string; content: string; senderName: string; senderId: string } | null>(null);
  const [recallingMessageId, setRecallingMessageId] = useState<string | null>(null);

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
    if (!chat || !chat.id || chat.isPending) return;
    const now = Date.now();

    if (isTyping && !force && isTypingSentRef.current && now - lastTypingSentAtRef.current < TYPING_REFRESH_MS) {
      return;
    }

    if (!isTyping && !isTypingSentRef.current && !force) {
      return;
    }

    wsService.sendTyping(chat.id.toString(), isTyping);
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

  const fetchGroupDetails = useCallback(() => {
    if (!chat?.group || !chat.id) return;
    groupService.get(chat.id.toString())
      .then(group => {
        setGroupMemberCount(group.members.length);
        setGroupMemberIds(group.members.map(member => member.userId));
        setIsKicked(false);
      })
      .catch((err) => {
        setGroupMemberIds([]);
        if (err.message === 'You are not a member of this group') {
          setIsKicked(true);
        }
        if (!chat.totalMembers) {
          setGroupMemberCount(null);
        }
      });
  }, [chat]);

  useEffect(() => {
    if (!chat?.group || !chat.id) {
      queueMicrotask(() => {
        setGroupMemberCount(null);
        setGroupMemberIds([]);
        setIsKicked(false);
      });
      return;
    }

    if (chat.totalMembers) {
      queueMicrotask(() => setGroupMemberCount(chat.totalMembers ?? null));
    }

    fetchGroupDetails();

  }, [chat?.group, chat?.id, chat?.totalMembers, fetchGroupDetails]);

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
        setReplyingTo(null);
      }
      
      setIsSidebarOpen(false);
      setIsKicked(false);
      setHasMoreMessages(true);
      setIsLoadingMore(false);
      isLoadingMoreRef.current = false;
      isPrependingHistoryRef.current = false;
      oldestMessageCursorRef.current = null;

      if (chat && typeof chat.id === 'string' && !chat.isPending) {
        const conversationId = chat.id;
        messageService.history(conversationId, undefined, HISTORY_PAGE_SIZE).then(async res => {
          const responseItems = (res.items || res.content || []) as MessageHistoryItem[];
          
          if (responseItems.length < HISTORY_PAGE_SIZE) {
            setHasMoreMessages(false);
          }
          if (responseItems.length > 0) {
            oldestMessageCursorRef.current = responseItems[responseItems.length - 1].createdAt;
          }

          const historyMsgs: UserMessage[] = (await Promise.all(responseItems.map(async (m) => {
            const msgDate = new Date(m.createdAt);
            const timeStr = `${String(msgDate.getHours()).padStart(2, '0')}:${String(msgDate.getMinutes()).padStart(2, '0')}`;
            const isSender = currentUserId === m.senderId;
            const isRecalled = !!m.recalledAt;
            return {
              id: m.messageId || m.id,
              text: isRecalled ? 'Tin nhắn đã được thu hồi' : m.content,
              time: timeStr,
              dateKey: getMessageDateKey(msgDate),
              senderId: m.senderId,
              senderName: isSender ? 'Bạn' : await resolveSenderName(m.senderId),
              isSender,
              replyTo: isRecalled ? null : m.replyTo,
              isRecalled,
              attachmentIds: isRecalled ? undefined : m.attachmentIds
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
            scrollToBottom();
          }, 50);
        });
      } else {
        queueMicrotask(() => setLastMessageReaders([]));
      }
    }
  }, [chat, currentUserId]);

  const loadMoreMessages = async () => {
    if (isLoadingMoreRef.current || !hasMoreMessages || !chat || typeof chat.id !== "string") return;

    const cursor = oldestMessageCursorRef.current;
    if (!cursor) {
      setHasMoreMessages(false);
      return;
    }

    const container = scrollContainerRef.current;
    const scrollHeightBefore = container?.scrollHeight ?? 0;
    const scrollTopBefore = container?.scrollTop ?? 0;
    let restoreScheduled = false;

    isLoadingMoreRef.current = true;
    setIsLoadingMore(true);

    try {
      const res = await messageService.history(chat.id, cursor, HISTORY_PAGE_SIZE);
      const responseItems = (res.items || res.content || []) as MessageHistoryItem[];

      if (responseItems.length < HISTORY_PAGE_SIZE) {
        setHasMoreMessages(false);
      }

      if (responseItems.length === 0) return;

      oldestMessageCursorRef.current = responseItems[responseItems.length - 1].createdAt;

      const historyMsgs: UserMessage[] = (await Promise.all(responseItems.map(async (m) => {
        const msgDate = new Date(m.createdAt);
        const timeStr = `${String(msgDate.getHours()).padStart(2, "0")}:${String(msgDate.getMinutes()).padStart(2, "0")}`;
        const isSender = currentUserId === m.senderId;
        const isRecalled = !!m.recalledAt;
        return {
          id: m.messageId || m.id,
          text: isRecalled ? "Tin nhắn đã được thu hồi" : m.content,
          time: timeStr,
          dateKey: getMessageDateKey(msgDate),
          senderId: m.senderId,
          senderName: isSender ? "Bạn" : await resolveSenderName(m.senderId),
          isSender,
          replyTo: isRecalled ? null : m.replyTo,
          isRecalled,
          attachmentIds: isRecalled ? undefined : m.attachmentIds
        };
      }))).reverse();

      isPrependingHistoryRef.current = true;
      setUserMessages(prev => {
        const merged = [...historyMsgs];
        prev.forEach(message => {
          if (message.id && merged.some(existing => existing.id === message.id)) return;
          merged.push(message);
        });
        return merged;
      });

      if (container) {
        restoreScheduled = true;
        window.requestAnimationFrame(() => {
          window.requestAnimationFrame(() => {
            const nextScrollTop = container.scrollHeight - scrollHeightBefore + scrollTopBefore;
            container.scrollTop = Math.max(nextScrollTop, 0);
            lastScrollHeightRef.current = container.scrollHeight;
            isPrependingHistoryRef.current = false;
            isLoadingMoreRef.current = false;
            setIsLoadingMore(false);
          });
        });
      }
    } catch (err) {
      console.error("Failed to load more messages:", err);
    } finally {
      if (!restoreScheduled) {
        isPrependingHistoryRef.current = false;
        isLoadingMoreRef.current = false;
        setIsLoadingMore(false);
      }
    }
  };

  const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
    if (e.currentTarget.scrollTop <= HISTORY_TOP_LOAD_THRESHOLD_PX) {
      void loadMoreMessages();
    }
  };

  useEffect(() => {
    previousChatRef.current = chat;
    setTimeout(() => {
      scrollToBottom();
      if (scrollContainerRef.current) {
        lastScrollHeightRef.current = scrollContainerRef.current.scrollHeight;
      }
    }, 10);
  }, [chat, currentUserId, scrollToBottom]);

  useEffect(() => {
    const container = scrollContainerRef.current;
    if (!container) return;

    const observer = new ResizeObserver(() => {
      const { scrollTop, scrollHeight, clientHeight } = container;
      if (isPrependingHistoryRef.current) {
        lastScrollHeightRef.current = scrollHeight;
        return;
      }
      if (lastScrollHeightRef.current && (scrollTop + clientHeight >= lastScrollHeightRef.current - 150)) {
        scrollToBottom();
      }
      lastScrollHeightRef.current = scrollHeight;
    });

    if (container.firstElementChild) {
      observer.observe(container.firstElementChild);
    }

    return () => observer.disconnect();
  }, [scrollToBottom]);

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
            text: data.content || (data.attachmentIds?.length ? '[Tệp đính kèm]' : ''), 
            time: timeStr,
            dateKey: getMessageDateKey(createdAt),
            senderId: data.senderId,
            senderName,
            isSender: false,
            replyTo: data.replyTo,
            attachmentIds: data.attachmentIds
          }];
        });
        setTimeout(() => {
          scrollToBottom(true);
        }, 100);
        void markConversationRead(data.conversationId, data.messageId)
          .then(() => refreshLastMessageReaders(data.conversationId));
      }

      if (isMessageReadFrame(msg) && msg.data.conversationId === chat?.id) {
        void refreshLastMessageReaders(msg.data.conversationId);
      }

      if (isMessageRecalledFrame(msg) && msg.data.conversationId === chat?.id) {
        setUserMessages(prev => prev.map(m => 
          m.id === msg.data.messageId 
            ? { ...m, text: 'Tin nhắn đã được thu hồi', isRecalled: true, replyTo: null, attachmentIds: undefined } 
            : m
        ));
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
      
      if (isGroupMemberEventFrame(msg) && msg.data.conversationId === chat?.id.toString()) {
        fetchGroupDetails();
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

  const handleRecallMessage = async (messageId: string) => {
    try {
      await messageService.recall(messageId);
      setUserMessages(prev => prev.map(msg => 
        msg.id === messageId ? { ...msg, text: 'Tin nhắn đã được thu hồi', isRecalled: true, replyTo: null, attachmentIds: undefined } : msg
      ));
      setRecallingMessageId(null);
    } catch (error) {
      console.error('Failed to recall message:', error);
    }
  };

  const handlePaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    const items = e.clipboardData.items;
    const newFiles: File[] = [];
    for (let i = 0; i < items.length; i++) {
      if (items[i].kind === 'file') {
        const file = items[i].getAsFile();
        if (file) {
          let finalFile = file;
          // When pasting raw image pixels, browsers default the file name to 'image.png'
          if (file.type.startsWith('image/') && file.name === 'image.png') {
            const uniqueName = `image_${Date.now()}_${Math.random().toString(36).substring(2, 8)}.png`;
            finalFile = new File([file], uniqueName, { type: file.type });
          }
          newFiles.push(finalFile);
        }
      }
    }
    if (newFiles.length > 0) {
      setPendingAttachments(prev => [...prev, ...newFiles]);
    }
  };

  const handleKeyDown = async (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && (inputText.trim() || pendingAttachments.length > 0) && !isUploading && chat) {
      const text = inputText.trim();
      const currentReply = replyingTo;
      const currentAttachments = [...pendingAttachments];
      setInputText("");
      setReplyingTo(null);
      setPendingAttachments([]);
      sendTypingState(false, true);
      clearOwnTypingTimers();
      
      const now = new Date();
      const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
      const dateKey = getMessageDateKey(now);
      
      const optimisticId = `optimistic-${Date.now()}`;

      // Optimistic update
      setUserMessages(prev => [...prev, { 
        id: optimisticId, 
        text: text || (currentAttachments.length > 0 ? '[Tệp đính kèm]' : ''), 
        time: timeStr, 
        dateKey, 
        senderId: currentUserId || undefined, 
        senderName: 'Bạn', 
        isSender: true,
        replyTo: currentReply ? { messageId: currentReply.id, senderId: currentReply.senderId, preview: currentReply.content } : null,
        attachmentIds: undefined 
      }]);
      
      setTimeout(() => {
        scrollToBottom(true);
      }, 50);
      
      setIsUploading(true);
      try {
        let conversationId = String(chat.id);

        if (chat.pendingDirectUserId) {
          const conversation = await conversationService.createDirect(chat.pendingDirectUserId);
          conversationId = conversation.id;
        }

        let attachmentIds: string[] = [];
        if (currentAttachments.length > 0) {
          const uploadPromises = currentAttachments.map(file => mediaService.uploadAttachment(conversationId, file));
          const uploadedMedia = await Promise.all(uploadPromises);
          attachmentIds = uploadedMedia.map(m => m.id);
        }

        const sentMessage = await messageService.send({
          conversationId,
          content: text || undefined,
          attachmentIds: attachmentIds.length > 0 ? attachmentIds : undefined,
          replyToMessageId: currentReply?.id
        });

        setUserMessages(prev => {
          const next = prev.map(message => message.id === optimisticId
            ? {
                ...message,
                id: sentMessage?.messageId || sentMessage?.id || optimisticId,
                text: sentMessage?.content ?? message.text,
                time: sentMessage?.createdAt
                  ? `${String(new Date(sentMessage.createdAt).getHours()).padStart(2, '0')}:${String(new Date(sentMessage.createdAt).getMinutes()).padStart(2, '0')}`
                  : message.time,
                attachmentIds: sentMessage?.attachmentIds
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
        console.error('Failed to send message:', error);
      } finally {
        setIsUploading(false);
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
    <div className="flex-1 h-screen flex flex-row">
      <div className="flex-1 flex flex-col bg-[#eef0f1] relative min-w-0">
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
              {isKicked ? (
                <span>Bạn đã rời nhóm</span>
              ) : (
                <span 
                  className={chat.group ? "cursor-pointer hover:underline" : ""} 
                  onClick={() => chat.group && setIsSidebarOpen(!isSidebarOpen)}
                >
                  {chat.group ? `${groupMemberCount ?? chat.totalMembers ?? 0} thành viên` : (chat.presenceStatus === 'online' ? 'Trực tuyến' : 'Ngoại tuyến')}
                </span>
              )}
            </div>
          </div>
        </div>
        <div className="flex items-center gap-1 text-gray-600">
          <button
            onClick={() => {
              setIsMediaSidebarOpen(!isMediaSidebarOpen);
              setIsSidebarOpen(false);
            }}
            className={`p-1.5 rounded-md transition-colors ${isMediaSidebarOpen ? 'bg-gray-100 text-[#0068ff]' : 'hover:bg-gray-100'}`}
            title="Kho lưu trữ"
          >
            <Folder size={18} />
          </button>
          {chat.group && !isKicked && (
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

      <div 
        ref={scrollContainerRef}
        id="chat-scroll-container"
        onScroll={handleScroll}
        className="relative flex-1 overflow-y-auto px-4 py-2 pb-3 flex flex-col gap-3"
      >
        <div className="flex flex-col gap-3 min-h-min">
          {isLoadingMore && (
            <div className="pointer-events-none sticky top-8 z-20 flex h-0 justify-center overflow-visible">
              <div className="flex items-center gap-2 px-3 py-1.5 text-[12px] font-medium text-[#7589A3]">
                <span className="h-3.5 w-3.5 rounded-full border-2 border-[#c8d2df] border-t-[#7589A3] animate-spin" />
                <span>Đang tải tin nhắn...</span>
              </div>
            </div>
          )}
        <div className="flex-1 min-h-0"></div>
        {userMessages.length > 0 && (
          <Daystamp date={new Date().toISOString()} />
        )}
        
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
                <div key={index} id={msg.id ? `msg-${msg.id}` : undefined} className="flex w-full justify-end group transition-colors duration-500 rounded-lg">
                  <div className="max-w-[70%] flex flex-col items-end">
                    {msg.replyTo && (
                      <div 
                        className="bg-white border border-[#e5e7eb] border-l-[3px] border-l-[#0068ff] shadow-sm px-3 py-2 text-sm rounded-t-lg rounded-bl-lg mb-[2px] cursor-pointer hover:bg-gray-50 transition-colors w-fit max-w-full text-left"
                        onClick={() => {
                           const el = document.getElementById(`msg-${msg.replyTo!.messageId}`);
                           el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
                           el?.classList.add('bg-blue-50');
                           setTimeout(() => el?.classList.remove('bg-blue-50'), 1500);
                        }}
                      >
                         <div className="font-semibold text-[#0068ff] text-[12px] mb-0.5 line-clamp-1">Trả lời tin nhắn</div>
                         <div className="text-gray-500 line-clamp-2 text-[12px] leading-snug">{msg.replyTo.preview}</div>
                      </div>
                    )}
                    <div className="flex items-center gap-2 w-full justify-end">
                      <div className="flex items-center opacity-0 group-hover:opacity-100 transition-opacity mr-2 gap-1.5">
                         {!msg.isRecalled && (
                           <>
                             <button onClick={() => msg.id && setRecallingMessageId(msg.id)} className="w-[28px] h-[28px] flex items-center justify-center rounded-full bg-white border border-gray-200 text-gray-500 hover:text-red-500 hover:bg-gray-50 shadow-sm transition-colors" title="Thu hồi">
                               <RotateCcw size={14} />
                             </button>
                             <button onClick={() => msg.id && setReplyingTo({ id: msg.id, content: msg.text, senderName: msg.senderName || 'Bạn', senderId: msg.senderId! })} className="w-[28px] h-[28px] flex items-center justify-center rounded-full bg-white border border-gray-200 text-gray-500 hover:text-[#0068ff] hover:bg-gray-50 shadow-sm transition-colors" title="Trả lời">
                               <ZalordReplyIcon className="w-[15px] h-[15px]" />
                             </button>
                           </>
                         )}
                      </div>
                      <div className={`rounded-lg px-3 py-2 shadow-sm border w-fit max-w-full ${msg.isRecalled ? 'bg-white border-gray-200' : 'bg-[#e5efff] border-[#cce1ff]'}`}>
                        <div className={`text-[15px] ${msg.isRecalled ? 'text-gray-400 italic' : 'text-[#081c36]'}`}>
                          {msg.text && <div>{msg.text}</div>}
                          {!msg.isRecalled && msg.attachmentIds && msg.attachmentIds.length > 0 && (
                            <div className="flex flex-col gap-1 mt-1">
                              {msg.attachmentIds.map(id => <AttachmentItem key={id} id={id} />)}
                            </div>
                          )}
                        </div>
                        <div className={`text-[12px] mt-1 text-right ${msg.isRecalled ? 'text-gray-400' : 'text-[#7589A3]'}`}>{msg.time}</div>
                      </div>
                    </div>
                  </div>
                </div>
              );
            }

            return (
              <div key={index} id={msg.id ? `msg-${msg.id}` : undefined} className="flex w-full justify-start items-start gap-2.5 group transition-colors duration-500 rounded-lg">
                {shouldReserveIncomingAvatarSpace && (
                  shouldShowIncomingAvatar ? (
                    <Avatar 
                      url={chat?.group ? undefined : chat?.avatarUrl}
                      name={avatarText} 
                      className="w-10 h-10 text-[14px]" 
                    />
                  ) : (
                    <div className="w-10 flex-shrink-0" />
                  )
                )}
                <div className="max-w-[70%] flex flex-col items-start">
                  {chat?.group && isFirstMessageInIncomingGroup && msg.senderName && (
                    <div className="text-[#7589A3] text-[12px] font-medium mb-1 pl-1">{msg.senderName}</div>
                  )}
                  {msg.replyTo && (
                    <div 
                      className="bg-white border border-[#e5e7eb] border-l-[3px] border-l-[#0068ff] shadow-sm px-3 py-2 text-sm rounded-t-lg rounded-br-lg mb-[2px] cursor-pointer hover:bg-gray-50 transition-colors w-fit max-w-full text-left"
                      onClick={() => {
                         const el = document.getElementById(`msg-${msg.replyTo!.messageId}`);
                         el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
                         el?.classList.add('bg-blue-50');
                         setTimeout(() => el?.classList.remove('bg-blue-50'), 1500);
                      }}
                    >
                       <div className="font-semibold text-[#0068ff] text-[12px] mb-0.5 line-clamp-1">Trả lời tin nhắn</div>
                       <div className="text-gray-500 line-clamp-2 text-[12px] leading-snug">{msg.replyTo.preview}</div>
                    </div>
                  )}
                  <div className="flex items-center gap-2 w-full justify-start">
                    <div className="bg-white border-[#e5e7eb] rounded-lg px-3 py-2 shadow-sm border w-fit max-w-full">
                      <div className={`text-[15px] ${msg.isRecalled ? 'text-gray-400 italic' : 'text-[#081c36]'}`}>
                        {msg.text && <div>{msg.text}</div>}
                        {!msg.isRecalled && msg.attachmentIds && msg.attachmentIds.length > 0 && (
                          <div className="flex flex-col gap-1 mt-1">
                            {msg.attachmentIds.map(id => <AttachmentItem key={id} id={id} />)}
                          </div>
                        )}
                      </div>
                      <div className={`text-[12px] mt-1 text-right ${msg.isRecalled ? 'text-gray-400' : 'text-[#7589A3]'}`}>{msg.time}</div>
                    </div>
                    {!msg.isRecalled && (
                      <div className="flex items-center opacity-0 group-hover:opacity-100 transition-opacity ml-1">
                         <button onClick={() => msg.id && setReplyingTo({ id: msg.id, content: msg.text, senderName: msg.senderName || 'Ai đó', senderId: msg.senderId! })} className="w-[28px] h-[28px] flex items-center justify-center rounded-full bg-white border border-gray-200 text-gray-500 hover:text-[#0068ff] hover:bg-gray-50 shadow-sm transition-colors" title="Trả lời">
                           <ZalordReplyIcon className="w-[15px] h-[15px]" />
                         </button>
                      </div>
                    )}
                  </div>
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
      </div>

      <AddMembersModal
        isOpen={isAddMembersModalOpen}
        onClose={() => setIsAddMembersModalOpen(false)}
        onConfirm={handleAddMembers}
        existingMemberIds={groupMemberIds}
      />

      {recallingMessageId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-white rounded-lg p-6 w-96 shadow-xl text-center flex flex-col items-center">
            <h3 className="text-lg font-semibold text-[#081c36] mb-2">Thu hồi tin nhắn</h3>
            <p className="text-gray-600 mb-6 text-sm">Bạn có chắc muốn thu hồi tin nhắn này không?</p>
            <div className="flex justify-end gap-3 w-full">
              <button
                onClick={() => setRecallingMessageId(null)}
                className="flex-1 py-2 text-[15px] font-medium text-[#081c36] bg-[#eef0f1] hover:bg-[#dfe2e7] rounded-md transition-colors"
              >
                Hủy
              </button>
              <button
                onClick={() => handleRecallMessage(recallingMessageId)}
                className="flex-1 py-2 text-[15px] font-medium text-white bg-red-600 hover:bg-red-700 rounded-md transition-colors"
              >
                Thu hồi
              </button>
            </div>
          </div>
        </div>
      )}

      {isKicked ? (
        <div className="bg-[#e9eaec] flex items-center justify-center h-[90px] text-gray-500 text-[14px] border-t border-[#d6dbe1] flex-shrink-0">
          Bạn không còn là thành viên của nhóm này
        </div>
      ) : (
      <div className="bg-white flex flex-col flex-shrink-0">
        {replyingTo && (
          <div className="flex items-center justify-between px-4 py-2 bg-[#f2f4f7] border-t border-[#d6dbe1] text-[13px] text-[#081c36]">
            <div className="flex flex-col border-l-[3px] border-[#0068ff] pl-2 overflow-hidden">
              <span className="font-semibold text-[#0068ff] mb-0.5">Trả lời {replyingTo.senderName}</span>
              <span className="text-gray-600 truncate">{replyingTo.content}</span>
            </div>
            <button onClick={() => setReplyingTo(null)} className="text-gray-400 hover:text-gray-600 p-1 rounded-full hover:bg-gray-200 transition-colors">
              <X size={18} />
            </button>
          </div>
        )}
        <div className="flex items-center gap-0.5 text-[#001a33] px-1.5 h-[46px] border-t border-b border-[#d6dbe1]">
          <div title="Gửi Sticker" className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors"><ZalordStickerIcon className="w-6 h-6" /></div>
          <div title="Gửi hình ảnh" className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors"><ZalordPhotoIcon className="w-6 h-6" /></div>
          <div title="Đính kèm File" onClick={() => fileInputRef.current?.click()} className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors"><ZalordAttachIcon className="w-6 h-6" /></div>
          <div title="Gửi danh thiếp" className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors"><ZalordNamecardIcon className="w-6 h-6" /></div>
          <div title="Chụp kèm với cửa sổ Zalo (Alt + Ctrl + S)" className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors"><ZalordScreenshotIcon className="w-6 h-6" /></div>
          <div title="Định dạng tin nhắn (Ctrl + Shift + X)" className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors"><ZalordTextFormatIcon className="w-6 h-6" /></div>
          <div title="Chèn tin nhắn nhanh" className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors"><ZalordQuickMsgIcon className="w-6 h-6" /></div>
          <div title="Gửi nhanh số tài khoản" className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors"><ZalordBankCardIcon className="w-6 h-6" /></div>
          <div title="Tùy chọn thêm" className="w-8 h-8 flex items-center justify-center rounded-md cursor-pointer hover:bg-gray-100 transition-colors"><ZalordMoreIcon className="w-6 h-6" /></div>
          <input
            type="file"
            multiple
            className="hidden"
            ref={fileInputRef}
            onChange={(e) => {
              const newFiles = e.target.files ? Array.from(e.target.files) : [];
              if (newFiles.length > 0) {
                setPendingAttachments(prev => [...prev, ...newFiles]);
              }
              e.target.value = '';
            }}
          />
        </div>
        
        {pendingAttachments.length > 0 && (
          <div className="px-4 py-2 flex flex-wrap gap-2 border-b border-gray-100">
            {pendingAttachments.map((file, index) => (
              <PendingAttachmentItem 
                key={index} 
                file={file} 
                onRemove={() => setPendingAttachments(prev => prev.filter((_, i) => i !== index))} 
              />
            ))}
          </div>
        )}

        {/* Input Field */}
        <div className="flex items-center gap-2 px-4 py-1.5 relative bg-white">
          <input 
            type="text" 
            placeholder={`Nhập @, tin nhắn tới ${chat.name}`}
            value={inputText}
            onChange={handleInputChange}
            onKeyDown={handleKeyDown}
            onPaste={handlePaste}
            className="flex-1 bg-transparent border-none outline-none text-[15px] py-1 text-gray-800 placeholder-gray-500"
          />
        </div>
      </div>
      )}
      </div>

      {chat.group && isSidebarOpen && (
        <GroupSidebar groupId={String(chat.id)} />
      )}
      {isMediaSidebarOpen && (
        <MediaSidebar conversationId={String(chat.id)} onClose={() => setIsMediaSidebarOpen(false)} />
      )}
    </div>
  );
}
