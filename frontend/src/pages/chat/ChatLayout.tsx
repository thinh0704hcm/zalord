import SidebarNav from '../../components/chat/SidebarNav';
import ChatList from '../../components/chat/ChatList';
import ChatWindow from '../../components/chat/ChatWindow';

export default function ChatLayout() {
  return (
    <div className="flex h-screen w-full overflow-hidden bg-white">
      <SidebarNav />
      <ChatList />
      <ChatWindow />
    </div>
  );
}
