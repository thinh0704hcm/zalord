import { useState, useEffect } from 'react';
import { userService, type UserProfile } from '../../services/user';
import { inboxService } from '../../services/inbox';
import { X, Search, Camera, XCircle } from 'lucide-react';

interface Contact {
  id: string;
  name: string;
  phoneNumber: string;
  avatar: string;
  avatarUrl?: string | null;
}

const getInitials = (name: string) => {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return `${words[0][0]}${words[words.length - 1][0]}`.toUpperCase();
};

const getCurrentUserInitials = () => {
  try {
    const rawUser = localStorage.getItem('user');
    if (!rawUser) return '';
    const user = JSON.parse(rawUser) as { displayName?: string; username?: string; phoneNumber?: string };
    return getInitials(user.displayName || user.username || user.phoneNumber || '');
  } catch {
    return '';
  }
};

const toContact = (profile: UserProfile): Contact => ({
  id: profile.userId,
  name: profile.displayName,
  phoneNumber: profile.phoneNumber,
  avatar: getInitials(profile.displayName),
  avatarUrl: profile.avatarUrl,
});

const queryHasDigit = (value: string) => /\d/.test(value);


interface CreateGroupModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreateGroup?: (groupName: string, memberIds: string[], selectedAvatars: string[], totalMembers: number) => Promise<void> | void;
}

export default function CreateGroupModal({ isOpen, onClose, onCreateGroup }: CreateGroupModalProps) {
  const [groupName, setGroupName] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedContacts, setSelectedContacts] = useState<Set<string>>(new Set());
  const [contactResults, setContactResults] = useState<Contact[]>([]);
  const [recentContacts, setRecentContacts] = useState<Contact[]>([]);
  const [selectedContactMap, setSelectedContactMap] = useState<Record<string, Contact>>({});
  const [isSearchingContacts, setIsSearchingContacts] = useState(false);
  const [isLoadingRecentContacts, setIsLoadingRecentContacts] = useState(false);
  const [isCreatingGroup, setIsCreatingGroup] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [recentContactsError, setRecentContactsError] = useState('');
  const [createGroupError, setCreateGroupError] = useState('');

  useEffect(() => {
    if (!isOpen) {
      setGroupName('');
      setSearchQuery('');
      setSelectedContacts(new Set());
      setContactResults([]);
      setRecentContacts([]);
      setSelectedContactMap({});
      setIsSearchingContacts(false);
      setIsLoadingRecentContacts(false);
      setIsCreatingGroup(false);
      setSearchError('');
      setRecentContactsError('');
      setCreateGroupError('');
    }
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen) return;

    let cancelled = false;

    const loadRecentDirectContacts = async () => {
      setIsLoadingRecentContacts(true);
      setRecentContactsError('');

      try {
        const inbox = await inboxService.list(1, 20);
        if (cancelled) return;

        const directItems = inbox.items.filter(item => item.otherUserId);
        const contacts = await Promise.all(
          directItems.map(async item => toContact(await userService.findByUserId(item.otherUserId as string)))
        );

        if (!cancelled) {
          setRecentContacts(contacts);
        }
      } catch (error: any) {
        if (!cancelled) {
          setRecentContacts([]);
          setRecentContactsError(error.message || 'Không thể tải trò chuyện gần đây');
        }
      } finally {
        if (!cancelled) {
          setIsLoadingRecentContacts(false);
        }
      }
    };

    loadRecentDirectContacts();

    return () => {
      cancelled = true;
    };
  }, [isOpen]);

  useEffect(() => {
    const query = searchQuery.trim();
    if (!query) {
      setContactResults([]);
      setSearchError('');
      setIsSearchingContacts(false);
      return;
    }

    setIsSearchingContacts(true);
    setSearchError('');

    const timer = window.setTimeout(async () => {
      try {
        const contacts = queryHasDigit(query)
          ? [toContact(await userService.findByPhone(query.replace(/\s+/g, '')))]
          : (await userService.searchByName(query)).map(toContact);

        setContactResults(contacts);
        if (contacts.length === 0) {
          setSearchError('Không tìm thấy người dùng');
        }
      } catch (error: any) {
        setContactResults([]);
        setSearchError(error.message || 'Không tìm thấy người dùng');
      } finally {
        setIsSearchingContacts(false);
      }
    }, 350);

    return () => window.clearTimeout(timer);
  }, [searchQuery]);

  if (!isOpen) return null;

  const toggleContact = (contact: Contact) => {
    const newSelected = new Set(selectedContacts);
    if (newSelected.has(contact.id)) {
      newSelected.delete(contact.id);
    } else {
      newSelected.add(contact.id);
      setSelectedContactMap(prev => ({ ...prev, [contact.id]: contact }));
    }
    setSelectedContacts(newSelected);
  };

  const ContactItem = ({ contact }: { contact: Contact }) => {
    const isSelected = selectedContacts.has(contact.id);
    return (
      <div
        className="flex items-center gap-3 py-2 px-4 hover:bg-[#f3f5f6] cursor-pointer"
        onClick={() => toggleContact(contact)}
      >
        <div className={`w-5 h-5 rounded-full border flex items-center justify-center flex-shrink-0 ${isSelected ? 'border-[#0068ff] bg-[#0068ff]' : 'border-[#d6dbe1] bg-white'}`}>
          {isSelected && <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>}
        </div>
        {contact.avatarUrl ? (
          <img src={contact.avatarUrl} alt={contact.name} className="w-10 h-10 rounded-full object-cover" />
        ) : (
          <div className="w-10 h-10 rounded-full flex items-center justify-center text-white text-[14px] font-medium bg-gray-400">
            {contact.avatar}
          </div>
        )}
        <div className="min-w-0 flex flex-col">
          <span className="text-[14px] text-[#081c36] font-medium truncate">{contact.name}</span>
          <span className="text-[12px] text-[#7589A3] truncate">{contact.phoneNumber}</span>
        </div>
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
              placeholder="Nhập tên hoặc số điện thoại"
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
                <span className="text-[14px] font-semibold text-[#081c36]">{searchQuery.trim() ? 'Kết quả tìm kiếm' : 'Trò chuyện gần đây'}</span>
              </div>
              {!searchQuery.trim() ? (
                isLoadingRecentContacts ? (
                  <div className="px-4 py-6 text-center text-[13px] text-[#7589A3]">Đang tải trò chuyện gần đây...</div>
                ) : recentContactsError ? (
                  <div className="px-4 py-6 text-center text-[13px] text-[#7589A3]">{recentContactsError}</div>
                ) : recentContacts.length === 0 ? (
                  <div className="px-4 py-6 text-center text-[13px] text-[#7589A3]">Chưa có trò chuyện gần đây</div>
                ) : (
                  recentContacts.map(contact => (
                    <ContactItem key={`recent-${contact.id}`} contact={contact} />
                  ))
                )
              ) : isSearchingContacts ? (
                <div className="px-4 py-6 text-center text-[13px] text-[#7589A3]">Đang tìm kiếm...</div>
              ) : searchError ? (
                <div className="px-4 py-6 text-center text-[13px] text-[#7589A3]">{searchError}</div>
              ) : (
                contactResults.map(contact => (
                  <ContactItem key={`result-${contact.id}`} contact={contact} />
                ))
              )}
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
                  const contact = selectedContactMap[id];
                  if (!contact) return null;
                  return (
                    <div key={id} className="flex items-center justify-between bg-[#e5efff] rounded-full p-1 pr-1.5 gap-1 border border-transparent hover:border-[#0068ff] transition-colors cursor-default">
                      <div className="flex items-center gap-1.5 overflow-hidden flex-1">
                        {contact.avatarUrl ? (
                          <img src={contact.avatarUrl} alt={contact.name} className="w-6 h-6 rounded-full object-cover flex-shrink-0" />
                        ) : (
                          <div className="w-6 h-6 rounded-full flex items-center justify-center text-white text-[10px] font-medium flex-shrink-0 bg-gray-400">
                            {contact.avatar}
                          </div>
                        )}
                        <span className="text-[13px] font-medium text-[#0068ff] truncate">{contact.name}</span>
                      </div>
                      <XCircle
                        size={16}
                        fill="#0068ff"
                        color="white"
                        className="flex-shrink-0 cursor-pointer transition-opacity hover:opacity-80"
                        onClick={() => toggleContact(contact)}
                      />
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="border-t border-[#e1e4ea] p-3 flex items-center justify-end gap-3 flex-shrink-0 relative">
          {createGroupError && (
            <div className="absolute left-4 text-[13px] text-red-500 max-w-[260px] truncate">{createGroupError}</div>
          )}
          <button
            onClick={onClose}
            className="px-6 py-2 rounded-md bg-[#eaedf0] hover:bg-[#dfe2e7] text-[#081c36] font-medium text-[15px] transition-colors"
          >
            Hủy
          </button>
          <button
            disabled={selectedContacts.size < 2 || !groupName.trim() || isCreatingGroup}
            onClick={async () => {
              if (!onCreateGroup || !groupName.trim() || isCreatingGroup) return;

              setIsCreatingGroup(true);
              setCreateGroupError('');

              try {
                const memberIds = Array.from(selectedContacts);
                const avatars = memberIds
                  .map(id => selectedContactMap[id]?.avatar)
                  .filter(Boolean) as string[];
                const currentUserAvatar = getCurrentUserInitials();
                if (currentUserAvatar) avatars.push(currentUserAvatar);
                await onCreateGroup(groupName.trim(), memberIds, avatars, selectedContacts.size + 1);
              } catch (error: any) {
                setCreateGroupError(error.message || 'Không thể tạo nhóm');
              } finally {
                setIsCreatingGroup(false);
              }
            }}
            className={`px-6 py-2 rounded-md font-medium text-[15px] transition-colors ${selectedContacts.size >= 2 && groupName.trim() && !isCreatingGroup
                ? 'bg-[#0068ff] text-white hover:bg-[#0055d4] cursor-pointer'
                : 'bg-[#a3c9ff] text-white cursor-not-allowed'
              }`}
          >
            {isCreatingGroup ? 'Đang tạo...' : 'Tạo nhóm'}
          </button>
        </div>
      </div>
    </div>
  );
}
