import { useState } from 'react';
import SidebarNav from '../../components/chat/SidebarNav';
import ChatList from '../../components/chat/ChatList';
import ChatWindow from '../../components/chat/ChatWindow';

export interface Chat {
  id: number;
  name: string;
  message: string;
  time: string;
  unread: number;
  avatar: string | string[];
  totalMembers?: number;
  group?: boolean;
}

const initialChats: Chat[] = [
  { id: 1, name: 'Cloudward', message: 'Bạn: Mai mấy giờ họp á mn', time: '5 phút', unread: 0, avatar: 'C', group: true },
  { id: 2, name: 'Phan Thị Kiên', message: '[Hình ảnh]', time: '11 phút', unread: 2, avatar: 'P' },
  { id: 3, name: 'ĐIỂM THI TRƯỜNG ĐẠI...', message: 'Trung Tâm Dv Sinh Viên - Isinhvien ...', time: '6 giờ', unread: 99, avatar: 'Đ', group: true },
  { id: 4, name: 'SV E2 - BỮA CƠM YÊU TH...', message: 'Các em vào đăng ký suất ăn...', time: '9 giờ', unread: 55, avatar: 'S', group: true },
  { id: 5, name: 'NHÀ XE C5-C6(NHÓM 1)', message: 'Vũ Lê Thanh Bình: Nhà xe 9G xin ...', time: '23 giờ', unread: 0, avatar: '96', group: true },
  { id: 6, name: 'HỌC IELTS MIỄN PH...', message: 'Tuyết Anh: @All CẢNH BÁO CÓ ...', time: 'Hôm qua', unread: 0, avatar: 'H', group: true },
  { id: 7, name: 'KTPM2023.3', message: 'Ngọc Quí: Mấy e tham khảo thử nhé', time: 'Hôm qua', unread: 0, avatar: 'K', group: true },
  { id: 8, name: 'MIXUE KTX KHU B -...', message: 'Linh Nguyen: ...', time: 'Hôm qua', unread: 0, avatar: 'M', group: true },
];

export default function ChatLayout() {
  const [chats, setChats] = useState<Chat[]>(initialChats);
  const [activeChatId, setActiveChatId] = useState(7);
  const activeChat = chats.find(c => c.id === activeChatId);

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

  return (
    <div className="flex h-screen w-full overflow-hidden bg-white">
      <SidebarNav />
      <ChatList chats={chats} activeChatId={activeChatId} onSelectChat={setActiveChatId} onCreateGroup={handleCreateGroup} />
      <ChatWindow chat={activeChat} />
    </div>
  );
}
