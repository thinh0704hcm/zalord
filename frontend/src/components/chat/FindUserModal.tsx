import React, { useState, useEffect } from 'react';
import { X, MessageCircle } from 'lucide-react';
import { userService } from '../../services/user';
import type { UserProfile } from '../../services/user';

interface FindUserModalProps {
  isOpen: boolean;
  onClose: () => void;
  onStartChat?: (userId: string) => void;
}

export default function FindUserModal({ isOpen, onClose, onStartChat }: FindUserModalProps) {
  const [phone, setPhone] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [userProfile, setUserProfile] = useState<UserProfile | null>(null);

  useEffect(() => {
    if (!isOpen) {
      setPhone('');
      setError('');
      setUserProfile(null);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!phone.trim()) return;

    setLoading(true);
    setError('');
    setUserProfile(null);

    try {
      const profile = await userService.findByPhone(phone.trim());
      setUserProfile(profile);
    } catch (err: any) {
      setError(err.message || 'Không tìm thấy người dùng');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-md w-[400px] flex flex-col shadow-xl overflow-hidden">
        {/* Header */}
        <div className="h-[48px] px-4 flex items-center justify-between border-b border-[#e5e7eb]">
          <h2 className="text-[16px] font-medium text-[#081c36]">Thêm bạn</h2>
          <button onClick={onClose} className="p-1 hover:bg-[#f3f5f6] rounded-full transition-colors">
            <X size={20} className="text-[#081c36]" />
          </button>
        </div>

        {/* Content */}
        <div className="p-4 flex flex-col gap-4">
          <form onSubmit={handleSearch} className="flex gap-2">
            <div className="flex-1 bg-[#eaedf0] h-[40px] rounded-md flex items-center px-3 gap-2">
              <span className="text-gray-500 font-medium">+84</span>
              <div className="w-[1px] h-[20px] bg-gray-300"></div>
              <input
                type="text"
                placeholder="Số điện thoại"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                className="bg-transparent border-none outline-none w-full text-[14px] text-[#081c36]"
                autoFocus
              />
            </div>
            <button
              type="submit"
              disabled={loading || phone.trim().length < 9}
              className="px-4 bg-[#0068ff] text-white rounded-md text-[14px] font-medium disabled:bg-[#a3c9ff] disabled:cursor-not-allowed hover:bg-[#0055d4] transition-colors"
            >
              {loading ? 'Tìm...' : 'Tìm kiếm'}
            </button>
          </form>

          {error && (
            <div className="text-[14px] text-red-500 mt-2 text-center">
              {error}
            </div>
          )}

          {userProfile && (
            <div className="mt-4 border border-[#e5e7eb] rounded-lg p-4 flex flex-col items-center gap-3">
              <img
                src={userProfile.avatarUrl || `https://ui-avatars.com/api/?name=${encodeURIComponent(userProfile.displayName)}&background=0068ff&color=fff`}
                alt={userProfile.displayName}
                className="w-16 h-16 rounded-full object-cover border border-gray-200"
              />
              <div className="text-center">
                <h3 className="font-medium text-[16px] text-[#081c36]">{userProfile.displayName}</h3>
                <p className="text-[13px] text-gray-500 mt-0.5">{userProfile.phoneNumber}</p>
              </div>

              <div className="flex gap-2 mt-2 w-full">
                <button
                  onClick={() => onStartChat?.(userProfile.id)}
                  className="flex-1 flex items-center justify-center gap-1.5 py-2 bg-[#e5eefb] text-[#0068ff] hover:bg-[#d0e0f8] rounded-md font-medium text-[14px] transition-colors"
                >
                  <MessageCircle size={18} />
                  Nhắn tin
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
