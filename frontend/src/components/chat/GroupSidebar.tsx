import { useState, useEffect } from 'react';
import { groupService } from '../../services/group';
import { wsService } from '../../services/websocket';
import { isGroupMemberEventFrame } from '../../services/websocket';
import { userService } from '../../services/user';
import type { UserProfile } from '../../services/user';
import { Avatar } from './Avatar';
import { MoreHorizontal } from 'lucide-react';

const getStoredUser = () => {
  try {
    const userStr = localStorage.getItem('user');
    return userStr ? JSON.parse(userStr) : {};
  } catch {
    return {};
  }
};

interface GroupSidebarProps {
  groupId: string;
}

interface MemberWithProfile {
  userId: string;
  role: string;
  joinedAt: string;
  profile?: UserProfile;
}

export default function GroupSidebar({ groupId }: GroupSidebarProps) {
  const [members, setMembers] = useState<MemberWithProfile[]>([]);
  const [loading, setLoading] = useState(true);
  
  const fetchGroupData = async () => {
    try {
      setLoading(true);
      const groupData = await groupService.get(groupId);
      
      const membersWithProfiles = await Promise.all(
        groupData.members.map(async (member) => {
          try {
             const profile = await userService.findByUserId(member.userId);
             return { ...member, profile };
          } catch (err) {
             return member;
          }
        })
      );
      setMembers(membersWithProfiles);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchGroupData();
    const unsubscribe = wsService.onMessage((msg) => {
      if (isGroupMemberEventFrame(msg) && msg.data.conversationId === groupId) {
        fetchGroupData();
      }
    });
    return unsubscribe;
  }, [groupId]);

  const handleRemoveMember = async (userId: string) => {
     try {
       await groupService.removeMember(groupId, userId);
       setMembers(prev => prev.filter(m => m.userId !== userId));
     } catch (err) {
       console.error(err);
       alert('Không thể xóa thành viên');
     }
  };

  return (
    <div className="w-[340px] bg-white border-l border-[#d6dbe1] flex flex-col h-full flex-shrink-0 transition-all">
       <div className="h-[64px] flex items-center justify-center px-4 border-b border-[#d6dbe1] flex-shrink-0">
          <h2 className="font-semibold text-[16px] text-gray-900">Thông tin nhóm</h2>
       </div>
       <div className="flex-1 overflow-y-auto">
          <div className="px-4 py-3 font-semibold text-[14px] text-gray-800 border-b border-gray-100 flex items-center justify-between">
            <span>Thành viên nhóm</span>
            <span className="text-gray-500 font-normal">{members.length}</span>
          </div>
          {loading ? (
             <div className="px-4 py-4 text-sm text-gray-500 text-center">Đang tải...</div>
          ) : (
             <div className="flex flex-col py-2">
                {members.map(member => (
                   <MemberItem key={member.userId} member={member} onRemove={() => handleRemoveMember(member.userId)} currentUserRole={members.find(m => m.userId === getStoredUser().id || m.userId === getStoredUser().userId)?.role || 'MEMBER'} />
                ))}
             </div>
          )}
       </div>
    </div>
  );
}

function MemberItem({ member, onRemove, currentUserRole }: { member: MemberWithProfile, onRemove: () => void, currentUserRole: string }) {
   const [showMenu, setShowMenu] = useState(false);
   const canRemove = (currentUserRole === 'OWNER' || currentUserRole === 'ADMIN') && member.role !== 'OWNER';

   return (
      <div className="flex items-center px-4 py-2 hover:bg-gray-50 relative group">
         <Avatar url={member.profile?.avatarUrl} name={member.profile?.displayName || 'Unknown'} className="w-10 h-10" />
         <div className="ml-3 flex-1 min-w-0">
            <div className="text-[14px] font-medium text-gray-900 truncate">{member.profile?.displayName || 'Unknown'}</div>
            <div className="text-[12px] text-gray-500">{member.role === 'OWNER' ? 'Trưởng nhóm' : member.role === 'ADMIN' ? 'Phó nhóm' : 'Thành viên'}</div>
         </div>
         {canRemove && (
            <button onClick={() => setShowMenu(!showMenu)} className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-gray-200 text-gray-500 opacity-0 group-hover:opacity-100 transition-opacity">
               <MoreHorizontal size={18} />
            </button>
         )}
         {showMenu && canRemove && (
            <>
               <div className="fixed inset-0 z-10" onClick={() => setShowMenu(false)}></div>
               <div className="absolute right-4 top-10 w-40 bg-white rounded-md shadow-[0_2px_10px_rgba(0,0,0,0.1)] border border-gray-100 py-1 z-20">
                  <button onClick={() => { setShowMenu(false); onRemove(); }} className="w-full text-left px-4 py-2 text-[14px] text-[#ff3333] hover:bg-gray-50 font-medium">
                     Xóa khỏi nhóm
                  </button>
               </div>
            </>
         )}
      </div>
   );
}
