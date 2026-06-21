import React, { useState } from 'react';
import { X, Search, Camera } from 'lucide-react';

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
];

const CONTACTS_BY_LETTER = [
  {
    letter: 'D',
    contacts: [
      { id: '3', name: 'Đỗ Đức Mạnh', avatar: 'ĐM' },
      { id: '4', name: 'Đỗ Trọng Hợp', avatar: 'ĐH' },
    ]
  },
  {
    letter: 'H',
    contacts: [
      { id: '5', name: 'Ho Dung', avatar: 'HD', avatarColor: 'bg-blue-600' },
      { id: '6', name: 'Huỳnh Anh Quốc', avatar: 'HQ' },
    ]
  }
];

const FILTERS = ['Tất cả', 'Khách hàng', 'Gia đình', 'Công việc', 'Bạn bè', 'Trả lời sau'];

interface CreateGroupModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function CreateGroupModal({ isOpen, onClose }: CreateGroupModalProps) {
  const [groupName, setGroupName] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [activeFilter, setActiveFilter] = useState('Tất cả');
  const [selectedContacts, setSelectedContacts] = useState<Set<string>>(new Set());

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
      <div className="bg-white rounded-md w-[400px] flex flex-col shadow-xl overflow-hidden h-[600px] max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between px-4 h-[48px] border-b border-[#e1e4ea] flex-shrink-0">
          <h2 className="text-[16px] font-semibold text-[#081c36]">Tạo nhóm</h2>
          <button onClick={onClose} className="p-1 hover:bg-[#f3f5f6] rounded-md transition-colors text-[#081c36]">
            <X size={20} strokeWidth={1.5} />
          </button>
        </div>

        {/* Group Name Input */}
        <div className="flex items-center gap-3 px-4 py-4 flex-shrink-0">
          <div className="w-12 h-12 rounded-full border border-[#d6dbe1] flex items-center justify-center flex-shrink-0 cursor-pointer text-[#7589A3]">
            <Camera size={20} strokeWidth={1.5} />
          </div>
          <input 
            type="text" 
            placeholder="Nhập tên nhóm..." 
            value={groupName}
            onChange={(e) => setGroupName(e.target.value)}
            className="flex-1 border-none outline-none text-[15px] text-[#081c36] placeholder-[#7589A3]"
          />
        </div>

        {/* Search */}
        <div className="px-4 pb-3 flex-shrink-0">
          <div className="border border-[#0068ff] rounded-full flex items-center px-3 h-[36px] gap-2">
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

        {/* Filters */}
        <div className="px-4 pb-2 border-b border-[#e1e4ea] flex-shrink-0">
          <div className="flex items-center gap-2 overflow-x-auto no-scrollbar pb-1">
            {FILTERS.map(filter => (
              <button 
                key={filter}
                onClick={() => setActiveFilter(filter)}
                className={`whitespace-nowrap px-3 py-1 rounded-full text-[13px] font-medium transition-colors ${
                  activeFilter === filter 
                    ? 'bg-[#0068ff] text-white' 
                    : 'bg-[#eaedf0] text-[#081c36] hover:bg-[#dfe2e7]'
                }`}
              >
                {filter}
              </button>
            ))}
          </div>
        </div>

        {/* Contacts List */}
        <div className="flex-1 overflow-y-auto">
          <div className="py-2">
            <div className="px-4 py-1">
              <span className="text-[14px] font-semibold text-[#081c36]">Trò chuyện gần đây</span>
            </div>
            {RECENT_CONTACTS.map(contact => (
              <ContactItem key={`recent-${contact.id}`} contact={contact} />
            ))}

            {CONTACTS_BY_LETTER.map(section => (
              <div key={section.letter}>
                <div className="px-4 py-2 mt-1">
                  <span className="text-[14px] font-semibold text-[#081c36]">{section.letter}</span>
                </div>
                {section.contacts.map(contact => (
                  <ContactItem key={contact.id} contact={contact} />
                ))}
              </div>
            ))}
          </div>
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
            className={`px-6 py-2 rounded-md font-medium text-[15px] transition-colors ${
              selectedContacts.size > 0 
                ? 'bg-[#0068ff] text-white hover:bg-[#0055d4]' 
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
