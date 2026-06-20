import { useState } from 'react';
import SidebarNav from '../../components/chat/SidebarNav';
import ChatList from '../../components/chat/ChatList';
import ChatWindow from '../../components/chat/ChatWindow';

export const mockChats = [
  { id: 0, name: 'Mocker', message: 'Bắt đầu chat với Mocker', time: 'Vừa xong', unread: 0, avatar: 'M' },
  { id: 1, name: 'PREP | Workshop Lộ trìn...', message: 'Trịnh Vũ Thu Hà đã tham gia cộng ...', time: '2 phút', unread: 0, avatar: 'P', group: true },
  { id: 2, name: 'P 1201E2', message: 'Trần Tiến: Hiện trên web ktx có Thông...', time: '20 phút', unread: 5, avatar: 'P', group: true },
  { id: 3, name: 'ĐIỂM THI TRƯỜNG ĐẠI...', message: 'Trung Tâm Dv Sinh Viên - Isinhvien ...', time: '6 giờ', unread: 99, avatar: 'Đ', group: true },
  { id: 4, name: 'SV E2 - BỮA CƠM YÊU TH...', message: 'Các em vào đăng ký suất ăn...', time: '9 giờ', unread: 55, avatar: 'S', group: true },
  { id: 5, name: 'NHÀ XE C5-C6(NHÓM 1)', message: 'Vũ Lê Thanh Bình: Nhà xe 9G xin ...', time: '23 giờ', unread: 0, avatar: '96', group: true },
  { id: 6, name: 'HỌC IELTS MIỄN PH...', message: 'Tuyết Anh: @All CẢNH BÁO CÓ ...', time: 'Hôm qua', unread: 0, avatar: 'H', group: true },
  { id: 7, name: 'KTPM2023.3', message: 'Ngọc Quí: Mấy e tham khảo thử nhé', time: 'Hôm qua', unread: 0, avatar: 'K', group: true },
  { id: 8, name: 'MIXUE KTX KHU B -...', message: 'Linh Nguyen: ...', time: 'Hôm qua', unread: 0, avatar: 'M', group: true },
];

export default function ChatLayout() {
  const [activeChatId, setActiveChatId] = useState(7);
  const activeChat = mockChats.find(c => c.id === activeChatId);

  return (
    <div className="flex h-screen w-full overflow-hidden bg-white">
      <SidebarNav />
      <ChatList chats={mockChats} activeChatId={activeChatId} onSelectChat={setActiveChatId} />
      <ChatWindow chat={activeChat} />
    </div>
  );
}
