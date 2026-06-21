import { useState, useEffect } from 'react';
import { X, Search, Camera, XCircle } from 'lucide-react';

interface Contact {
  id: string;
  name: string;
  avatar: string;
  avatarColor?: string;
  section?: string;
}

const RECENT_CONTACTS: Contact[] = [
  { id: '1', name: 'Đỗ Đức Mạnh', avatar: 'ĐM' },
  { id: '2', name: 'Trần Đức Thịnh', avatar: 'TT' },
  { id: '3', name: 'Đỗ Trọng Hợp', avatar: 'ĐH', avatarColor: 'bg-[#4a90e2]' },
  { id: '4', name: 'Ho Dung', avatar: 'HD', avatarColor: 'bg-[#f5a623]' },
  { id: '5', name: 'Huỳnh Anh Quốc', avatar: 'HQ', avatarColor: 'bg-[#7ed321]' },
  { id: '6', name: 'Nguyễn Phúc Thịnh', avatar: 'NT', avatarColor: 'bg-[#9013fe]' },
  { id: '7', name: 'Lê Thanh Bình', avatar: 'LB', avatarColor: 'bg-[#d0021b]' },
  { id: '8', name: 'Phạm Ngọc Thạch', avatar: 'PT', avatarColor: 'bg-[#009688]' },
  { id: '9', name: 'Vũ Minh Tuấn', avatar: 'VT', avatarColor: 'bg-[#e91e63]' },
  { id: '10', name: 'Hoàng Thị Cúc', avatar: 'HC', avatarColor: 'bg-[#607d8b]' },
];



interface CreateGroupModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreateGroup?: (groupName: string, selectedAvatars: string[], totalMembers: number) => void;
}

export default function CreateGroupModal({ isOpen, onClose, onCreateGroup }: CreateGroupModalProps) {
  const [groupName, setGroupName] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedContacts, setSelectedContacts] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (!isOpen) {
      setGroupName('');
      setSearchQuery('');
      setSelectedContacts(new Set());
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const toggleContact = (id: string) => {
    const newSelected = new Set(selectedContacts);
    if (newSelected.has(id)) {
      newSelected.delete(id);
    } else {
      newSelected.add(id);
    }
    setSelectedContacts(newSelected);
  };

  const ContactItem = ({ contact }: { contact: Contact }) => {
    const isSelected = selectedContacts.has(contact.id);
    return (
      <div
        className="flex items-center gap-3 py-2 px-4 hover:bg-[#f3f5f6] cursor-pointer"
        onClick={() => toggleContact(contact.id)}
      >
        <div className={`w-5 h-5 rounded-full border flex items-center justify-center flex-shrink-0 ${isSelected ? 'border-[#0068ff] bg-[#0068ff]' : 'border-[#d6dbe1] bg-white'}`}>
          {isSelected && <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>}
        </div>
        <div className={`w-10 h-10 rounded-full flex items-center justify-center text-white text-[14px] font-medium ${contact.avatarColor || 'bg-gray-400'}`}>
          {contact.avatar}
        </div>
        <span className="text-[14px] text-[#081c36] font-medium">{contact.name}</span>
      </div>
    );
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-md w-[520px] flex flex-col shadow-xl overflow-hidden h-[640px] max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between px-4 h-[48px] border-b border-[#e1e4ea] flex-shrink-0">
          <h2 className="text-[16px] font-semibold text-[#081c36]">Tạo nhóm</h2>
          <button onClick={onClose} className="p-1 hover:bg-[#f3f5f6] rounded-md transition-colors text-[#081c36]">
            <X size={20} strokeWidth={1.5} />
          </button>
        </div>

        {/* Group Name Input */}
        <div className="flex items-center gap-4 px-4 pt-3 pb-3 flex-shrink-0">
          <div className="w-12 h-12 rounded-full border border-[#d6dbe1] flex items-center justify-center flex-shrink-0 cursor-pointer text-[#7589A3]">
            <Camera size={20} strokeWidth={1.5} />
          </div>
          <div className="flex-1 flex flex-col justify-end h-12 border-b-[1.5px] border-[#d6dbe1] focus-within:border-[#0068ff] transition-colors">
            <input
              type="text"
              placeholder="Nhập tên nhóm..."
              value={groupName}
              onChange={(e) => setGroupName(e.target.value)}
              className="w-full pb-1.5 outline-none text-[15px] text-[#081c36] placeholder-[#7589A3] bg-transparent"
            />
          </div>
        </div>

        {/* Search */}
        <div className="px-4 pb-1 flex-shrink-0">
          <div className="border border-[#d6dbe1] focus-within:border-[#0068ff] transition-colors rounded-full flex items-center px-3 h-[36px] gap-2">
            <Search size={16} className="text-[#7589A3]" strokeWidth={2} />
            <input
              type="text"
              placeholder="Nhập tên, số điện thoại, hoặc danh sách số điện thoại"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="flex-1 border-none outline-none text-[13px] text-[#081c36] placeholder-[#7589A3]"
            />
          </div>
        </div>



        {/* Main Content Area */}
        <div className="flex-1 flex overflow-hidden">
          {/* Left: Contacts List */}
          <div className="flex-1 overflow-y-auto">
            <div className="pt-1 pb-2">
              <div className="px-4 py-0">
                <span className="text-[14px] font-semibold text-[#081c36]">Trò chuyện gần đây</span>
              </div>
              {RECENT_CONTACTS.map(contact => (
                <ContactItem key={`recent-${contact.id}`} contact={contact} />
              ))}
            </div>
          </div>

          {/* Right: Selected Contacts Panel */}
          {selectedContacts.size > 0 && (
            <div className="w-[200px] border border-[#e1e4ea] rounded-md ml-2 mr-4 mb-4 flex flex-col flex-shrink-0 bg-white">
              <div className="px-3 py-2.5 flex items-center gap-2">
                <span className="text-[14px] font-semibold text-[#081c36]">Đã chọn</span>
                <span className="bg-[#e5efff] text-[#0068ff] text-[12px] font-medium px-2 py-0.5 rounded-full">
                  {selectedContacts.size}/100
                </span>
              </div>
              <div className="flex-1 overflow-y-auto px-2 pb-2 flex flex-col gap-2">
                {Array.from(selectedContacts).map(id => {
                  const contact = RECENT_CONTACTS.find(c => c.id === id);
                  if (!contact) return null;
                  return (
                    <div key={id} className="flex items-center justify-between bg-[#e5efff] rounded-full p-1 pr-1.5 gap-1 border border-transparent hover:border-[#0068ff] transition-colors cursor-default">
                      <div className="flex items-center gap-1.5 overflow-hidden flex-1">
                        <div className={`w-6 h-6 rounded-full flex items-center justify-center text-white text-[10px] font-medium flex-shrink-0 ${contact.avatarColor || 'bg-gray-400'}`}>
                          {contact.avatar}
                        </div>
                        <span className="text-[13px] font-medium text-[#0068ff] truncate">{contact.name}</span>
                      </div>
                      <XCircle
                        size={16}
                        fill="#0068ff"
                        color="white"
                        className="flex-shrink-0 cursor-pointer transition-opacity hover:opacity-80"
                        onClick={() => toggleContact(id)}
                      />
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="border-t border-[#e1e4ea] p-3 flex items-center justify-end gap-3 flex-shrink-0">
          <button
            onClick={onClose}
            className="px-6 py-2 rounded-md bg-[#eaedf0] hover:bg-[#dfe2e7] text-[#081c36] font-medium text-[15px] transition-colors"
          >
            Hủy
          </button>
          <button
            disabled={selectedContacts.size < 2 || !groupName.trim()}
            onClick={() => {
              if (onCreateGroup && groupName.trim()) {
                const avatars = Array.from(selectedContacts)
                  .map(id => RECENT_CONTACTS.find(c => c.id === id)?.avatar)
                  .filter(Boolean) as string[];
                avatars.push('Bạn');
                onCreateGroup(groupName.trim(), avatars, selectedContacts.size + 1);
              }
            }}
            className={`px-6 py-2 rounded-md font-medium text-[15px] transition-colors ${selectedContacts.size >= 2 && groupName.trim()
                ? 'bg-[#0068ff] text-white hover:bg-[#0055d4] cursor-pointer'
                : 'bg-[#a3c9ff] text-white cursor-not-allowed'
              }`}
          >
            Tạo nhóm
          </button>
        </div>
      </div>
    </div>
  );
}
