import { Search, UserPlus, Users, MoreHorizontal, ChevronDown } from 'lucide-react';

export default function ChatList() {
  const mockChats = [
    { id: 1, name: 'PREP | Workshop Lộ trìn...', message: 'Trịnh Vũ Thu Hà đã tham gia cộng ...', time: '2 phút', unread: 0, avatar: 'P' },
    { id: 2, name: 'P 1201E2', message: 'Trần Tiến: Hiện trên web ktx có Thông...', time: '20 phút', unread: 5, avatar: 'P', group: true },
    { id: 3, name: 'ĐIỂM THI TRƯỜNG ĐẠI...', message: 'Trung Tâm Dv Sinh Viên - Isinhvien ...', time: '6 giờ', unread: 99, avatar: 'Đ', group: true },
    { id: 4, name: 'SV E2 - BỮA CƠM YÊU TH...', message: 'Các em vào đăng ký suất ăn...', time: '9 giờ', unread: 55, avatar: 'S', group: true },
    { id: 5, name: 'NHÀ XE C5-C6(NHÓM 1)', message: 'Vũ Lê Thanh Bình: Nhà xe 9G xin ...', time: '23 giờ', unread: 0, avatar: '96', group: true },
    { id: 6, name: 'HỌC IELTS MIỄN PH...', message: 'Tuyết Anh: @All CẢNH BÁO CÓ ...', time: 'Hôm qua', unread: 0, avatar: 'H', group: true },
    { id: 7, name: 'KTPM2023.3', message: 'Ngọc Quí: Mấy e tham khảo thử nhé', time: 'Hôm qua', unread: 0, avatar: 'K', group: true, active: true },
    { id: 8, name: 'MIXUE KTX KHU B -...', message: 'Linh Nguyen: ...', time: 'Hôm qua', unread: 0, avatar: 'M', group: true },
  ];

  return (
    <div className="w-[344px] h-screen bg-white border-r border-gray-200 flex flex-col flex-shrink-0">
      {/* Search Header */}
      <div className="px-4 py-3 flex items-center gap-2 border-b border-gray-100">
        <div className="flex-1 bg-[#eaedf0] rounded-md flex items-center px-2.5 py-1.5 gap-1.5">
          <Search size={15} className="text-gray-500" />
          <input 
            type="text" 
            placeholder="Tìm kiếm" 
            className="bg-transparent border-none outline-none w-full text-[13px] text-gray-700 placeholder-gray-500"
          />
        </div>
        <button className="p-1.5 text-gray-600 hover:bg-gray-100 rounded-md">
          <UserPlus size={18} />
        </button>
        <button className="p-1.5 text-gray-600 hover:bg-gray-100 rounded-md">
          <Users size={18} />
        </button>
      </div>

      {/* Tabs */}
      <div className="flex items-center justify-between px-4 py-1.5 border-b border-gray-100">
        <div className="flex items-center gap-4 text-[13px] font-medium">
          <span className="text-[#0068ff] border-b-2 border-[#0068ff] pb-1 cursor-pointer">Tất cả</span>
          <span className="text-gray-600 pb-1 cursor-pointer hover:text-gray-900">Chưa đọc</span>
        </div>
        <div className="flex items-center gap-2 text-[12px] text-gray-600">
          <div className="flex items-center gap-1 cursor-pointer hover:text-gray-900">
            <span>Phân loại</span>
            <ChevronDown size={14} />
          </div>
          <MoreHorizontal size={16} className="cursor-pointer ml-1" />
        </div>
      </div>

      {/* Chat List */}
      <div className="flex-1 overflow-y-auto">
        {mockChats.map(chat => (
          <div 
            key={chat.id} 
            className={`flex items-start gap-3 px-4 py-2.5 cursor-pointer transition-colors ${chat.active ? 'bg-[#e5efff]' : 'hover:bg-gray-50'}`}
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
              <p className="text-[13px] text-gray-500 truncate leading-snug">{chat.message}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
