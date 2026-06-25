import { useState, useRef, useEffect } from 'react';
import { Camera, ChevronLeft, Image, Pencil, User, X, Bell } from 'lucide-react';
import { userService, type UserProfile } from '../../services/user';
import api from '../../services/api';
import { Avatar } from './Avatar';
import { 
  ZalordMessageFilledIcon,
  ZalordContactFilledIcon,
  ZalordMessageOutlineIcon, 
  ZalordContactOutlineIcon
} from './ZalordIcons';

type StoredUser = {
  displayName?: string;
  username?: string;
  phoneNumber?: string;
  avatarUrl?: string | null;
  bio?: string | null;
  gender?: string | null;
  dateOfBirth?: string | null;
};

type ProfileMode = 'view' | 'edit' | 'avatar';

const UNSET_PROFILE_VALUE = 'Chưa cập nhật';

const getStoredUser = (): StoredUser => {
  try {
    const rawUser = localStorage.getItem('user');
    return rawUser ? JSON.parse(rawUser) : {};
  } catch {
    return {};
  }
};

const getStoredUserName = () => {
  const user = getStoredUser();
  return user.displayName || user.username || user.phoneNumber || 'Người dùng';
};

const getStoredAvatarUrl = () => getStoredUser().avatarUrl || null;

const formatPhone = (phone?: string) => phone || UNSET_PROFILE_VALUE;

const formatDateOfBirth = (value?: string | null) => {
  if (!value) return UNSET_PROFILE_VALUE;

  const [year, month, day] = value.split('-');
  if (!year || !month || !day) return UNSET_PROFILE_VALUE;
  return `${day} tháng ${month}, ${year}`;
};

const getErrorMessage = (error: unknown, fallback: string) => {
  if (error instanceof Error) return error.message;
  return fallback;
};

export default function SidebarNav() {
  const [activeTab, setActiveTab] = useState('message');
  const [showNotificationMenu, setShowNotificationMenu] = useState(false);
  const [notificationsEnabled, setNotificationsEnabled] = useState(true);
  const [showProfilePopup, setShowProfilePopup] = useState(false);
  const [showAccountModal, setShowAccountModal] = useState(false);
  const [profileMode, setProfileMode] = useState<ProfileMode>('view');
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [userName, setUserName] = useState(getStoredUserName);
  const [avatarUrl, setAvatarUrl] = useState<string | null>(getStoredAvatarUrl);
  const [draftName, setDraftName] = useState(getStoredUserName);
  const [draftGender, setDraftGender] = useState('');
  const [draftBirthday, setDraftBirthday] = useState('');
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [updateError, setUpdateError] = useState('');
  const [isUpdating, setIsUpdating] = useState(false);
  const popupRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    let cancelled = false;

    userService.me()
      .then((nextProfile) => {
        if (cancelled) return;

        setProfile(nextProfile);
        setUserName(nextProfile.displayName);
        setDraftName(nextProfile.displayName);
        setDraftGender(nextProfile.gender ?? '');
        setDraftBirthday(nextProfile.dateOfBirth ?? '');
        setAvatarUrl(nextProfile.avatarUrl);

        const storedUser = getStoredUser();
        localStorage.setItem(
          'user',
          JSON.stringify({
            ...storedUser,
            ...nextProfile,
            username: nextProfile.phoneNumber,
          })
        );
      })
      .catch(() => {
        // Keep the cached identity when the profile endpoint is temporarily unavailable.
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (popupRef.current && !popupRef.current.contains(event.target as Node)) {
        setShowProfilePopup(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const openAccountProfile = () => {
    setProfileMode('view');
    setShowProfilePopup(false);
    setShowAccountModal(true);
    setUpdateError('');
  };

  const closeAccountModal = () => {
    setShowAccountModal(false);
    setProfileMode('view');
    setUpdateError('');
  };

  const beginProfileEdit = () => {
    setDraftName(profile?.displayName || userName);
    setDraftGender(profile?.gender ?? '');
    setDraftBirthday(profile?.dateOfBirth ?? '');
    setUpdateError('');
    setProfileMode('edit');
  };

  const applyProfileUpdate = async () => {
    const nextName = draftName.trim();
    if (!nextName) {
      setUpdateError('Tên hiển thị không được để trống');
      return;
    }

    setIsUpdating(true);
    setUpdateError('');
    try {
      const updatedProfile = await userService.updateMe({
        displayName: nextName,
        gender: draftGender || null,
        dateOfBirth: draftBirthday || null,
      });

      setProfile(updatedProfile);
      setUserName(updatedProfile.displayName);
      setDraftName(updatedProfile.displayName);
      setDraftGender(updatedProfile.gender ?? '');
      setDraftBirthday(updatedProfile.dateOfBirth ?? '');
      setAvatarUrl(updatedProfile.avatarUrl);

      const storedUser = getStoredUser();
      localStorage.setItem(
        'user',
        JSON.stringify({
          ...storedUser,
          ...updatedProfile,
          username: updatedProfile.phoneNumber,
        })
      );
      setProfileMode('view');
    } catch (error: unknown) {
      setUpdateError(getErrorMessage(error, 'Không thể cập nhật thông tin tài khoản'));
    } finally {
      setIsUpdating(false);
    }
  };

  const beginAvatarEdit = () => {
    setAvatarPreview(null);
    setAvatarFile(null);
    setUpdateError('');
    setProfileMode('avatar');
  };

  const applyAvatarUpdate = async () => {
    if (!avatarFile) {
      setProfileMode('view');
      return;
    }
    
    setIsUpdating(true);
    setUpdateError('');
    
    try {
      // 1. Get upload URL
      const uploadReq = await api.post('/media/upload-url', {
        kind: 'AVATAR',
        mimeType: avatarFile.type
      });
      const { mediaId, uploadUrl } = uploadReq.data.data;
      
      // 2. Upload file
      await fetch(uploadUrl, {
        method: 'PUT',
        body: avatarFile,
        headers: { 'Content-Type': avatarFile.type }
      });
      
      // 3. Finalize upload
      await api.post(`/media/${mediaId}/finalize`);
      
      // 4. Get the direct URL to save to profile
      const getUrlReq = await api.get(`/media/${mediaId}/url`);
      const finalAvatarUrl = getUrlReq.data.data.downloadUrl;

      const updatedProfile = await userService.updateMe({
        displayName: profile?.displayName || userName,
        gender: profile?.gender || null,
        dateOfBirth: profile?.dateOfBirth || null,
        avatarUrl: finalAvatarUrl,
      });

      setProfile(updatedProfile);
      setUserName(updatedProfile.displayName);
      setDraftName(updatedProfile.displayName);
      setDraftGender(updatedProfile.gender ?? '');
      setDraftBirthday(updatedProfile.dateOfBirth ?? '');
      setAvatarUrl(updatedProfile.avatarUrl);

      const storedUser = getStoredUser();
      localStorage.setItem(
        'user',
        JSON.stringify({
          ...storedUser,
          ...updatedProfile,
          username: updatedProfile.phoneNumber,
        })
      );
      setProfileMode('view');
    } catch (error: unknown) {
      console.error("Avatar update failed:", error);
      setUpdateError(getErrorMessage(error, 'Không thể cập nhật ảnh đại diện'));
    } finally {
      setIsUpdating(false);
    }
  };

  const storedUser = getStoredUser();
  const accountPhone = profile?.phoneNumber || storedUser.phoneNumber || storedUser.username;
  const accountGender = profile?.gender || storedUser.gender || UNSET_PROFILE_VALUE;
  const accountDateOfBirth = formatDateOfBirth(profile?.dateOfBirth ?? storedUser.dateOfBirth);

  return (
    <>
      <div className="relative z-[60] w-[64px] h-screen bg-[#005ae0] flex flex-col items-center py-4 justify-between flex-shrink-0">
        <div className="flex flex-col items-center w-full relative">
          <div className="relative mt-2" ref={popupRef}>
            <div 
              onClick={() => setShowProfilePopup(!showProfilePopup)}
              className="cursor-pointer transition-colors shadow-sm hover:opacity-90 rounded-full"
            >
              <Avatar
                url={avatarUrl}
                name={userName}
                className="w-10 h-10 text-[14px]"
              />
            </div>

            {showProfilePopup && (
              <div className="absolute top-0 left-[60px] z-[80] w-[292px] bg-white rounded-md shadow-[0_2px_10px_rgba(0,0,0,0.2)] border border-gray-200 py-1.5">
                <div className="px-4 py-2.5">
                  <h3 className="font-bold text-[16px] text-gray-900 truncate">{userName}</h3>
                </div>
                <div className="h-px bg-gray-200 my-1" />
                <div className="flex flex-col">
                  <button
                    type="button"
                    onClick={openAccountProfile}
                    className="px-4 py-2.5 text-left hover:bg-gray-100 cursor-pointer text-[14px] text-gray-700 transition-colors"
                  >
                    Hồ sơ của bạn
                  </button>
                  <div className="px-4 py-2.5 hover:bg-gray-100 cursor-pointer text-[14px] text-gray-700 transition-colors">
                    Cài đặt
                  </div>
                </div>
                <div className="h-px bg-gray-200 my-1" />
                <div 
                  onClick={() => {
                    localStorage.removeItem('token');
                    localStorage.removeItem('refreshToken');
                    localStorage.removeItem('user');
                    window.location.href = '/account/login';
                  }}
                  className="px-4 py-2.5 hover:bg-gray-100 cursor-pointer text-[14px] text-gray-700 transition-colors"
                >
                  Đăng xuất
                </div>
              </div>
            )}
          </div>
          
          <div className="flex flex-col w-full mt-6 items-center gap-0.5">
            <div 
              onClick={() => setActiveTab('message')}
              className={`w-[48px] h-[48px] rounded-xl flex items-center justify-center cursor-pointer transition-colors ${activeTab === 'message' ? 'bg-[#004bb9] text-white' : 'hover:bg-[#0051d1] text-white'}`}
            >
              {activeTab === 'message' ? <ZalordMessageFilledIcon /> : <ZalordMessageOutlineIcon />}
            </div>
            <div 
              onClick={() => setActiveTab('contact')}
              className={`w-[48px] h-[48px] rounded-xl flex items-center justify-center cursor-pointer transition-colors ${activeTab === 'contact' ? 'bg-[#004bb9] text-white' : 'hover:bg-[#0051d1] text-white'}`}
            >
              {activeTab === 'contact' ? <ZalordContactFilledIcon /> : <ZalordContactOutlineIcon />}
            </div>
          </div>
        </div>

        <div className="flex flex-col items-center w-full mb-2 gap-1.5">
          <div className="relative">
            <div 
              onClick={() => setShowNotificationMenu(!showNotificationMenu)}
              className={`w-[48px] h-[48px] rounded-xl flex items-center justify-center cursor-pointer transition-colors ${showNotificationMenu ? 'bg-[#004bb9] text-white' : 'hover:bg-[#0051d1] text-white'}`}
            >
              <Bell size={26} fill={showNotificationMenu ? "currentColor" : "none"} strokeWidth={2.2} />
            </div>
            
            {showNotificationMenu && (
              <>
                <div 
                  className="fixed inset-0 z-40"
                  onClick={() => setShowNotificationMenu(false)}
                />
                <div className="absolute left-[56px] bottom-0 w-[160px] bg-white rounded-lg shadow-[0_4px_20px_rgba(0,0,0,0.15)] border border-[#e7e9ee] overflow-hidden z-50 py-1">
                  <div 
                    className="flex items-center justify-between px-4 py-3 hover:bg-gray-50 cursor-pointer transition-colors"
                    onClick={() => setNotificationsEnabled(!notificationsEnabled)}
                  >
                    <span className="text-sm font-medium text-gray-800">Thông báo</span>
                    <div 
                      className={`relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors duration-200 ease-in-out ${notificationsEnabled ? 'bg-[#004bb9]' : 'bg-gray-200'}`}
                    >
                      <span
                        className={`inline-block h-4 w-4 transform rounded-full bg-white transition duration-200 ease-in-out shadow-sm ${notificationsEnabled ? 'translate-x-[18px]' : 'translate-x-0.5'}`}
                      />
                    </div>
                  </div>
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      {showAccountModal && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/55">
          <div className="w-[500px] max-w-[calc(100vw-32px)] overflow-hidden rounded-[4px] bg-white shadow-[0_8px_28px_rgba(0,0,0,0.22)]">
            <div className="flex h-[62px] items-center justify-between px-5 border-b border-[#e7e9ee]">
              <div className="flex items-center gap-2 min-w-0">
                {(profileMode === 'edit' || profileMode === 'avatar') && (
                  <button
                    type="button"
                    onClick={() => {
                      if (profileMode === 'avatar' && avatarPreview) {
                        setAvatarPreview(null);
                      } else {
                        setProfileMode('view');
                      }
                    }}
                    className="flex h-8 w-8 items-center justify-center rounded-md text-[#081c36] hover:bg-[#f0f2f5]"
                    title="Quay lại"
                  >
                    <ChevronLeft size={22} strokeWidth={1.8} />
                  </button>
                )}
                <h2 className="truncate text-[20px] font-semibold text-[#081c36]">
                  {profileMode === 'view' ? 'Thông tin tài khoản' : profileMode === 'edit' ? 'Cập nhật thông tin' : 'Cập nhật ảnh đại diện'}
                </h2>
              </div>
              <button
                type="button"
                onClick={closeAccountModal}
                className="flex h-9 w-9 items-center justify-center rounded-md text-[#081c36] hover:bg-[#f0f2f5]"
                title="Đóng"
              >
                <X size={24} strokeWidth={1.6} />
              </button>
            </div>

            {profileMode === 'view' ? (
              <>
                <div className="h-[214px] bg-[linear-gradient(135deg,#1f8c7c_0%,#72bf44_38%,#d4d65c_68%,#26734d_100%)]">
                  <div className="h-full w-full opacity-50 bg-[radial-gradient(circle_at_20%_20%,rgba(255,255,255,0.75),transparent_28%),repeating-linear-gradient(165deg,rgba(255,255,255,0.22)_0_8px,transparent_8px_28px)]" />
                </div>

                <div className="relative flex min-h-[100px] items-center gap-5 px-5 pb-5 pt-4 border-b-[6px] border-[#eef0f4]">
                  <div className="relative -mt-[56px] h-[100px] w-[100px] shrink-0 rounded-full border-4 border-white bg-[#2f80ed] shadow-sm flex items-center justify-center text-[36px] font-bold text-white overflow-visible">
                    <Avatar
                      url={avatarUrl}
                      name={userName}
                      className="w-full h-full text-[36px]"
                    />
                    <button onClick={beginAvatarEdit} className="absolute bottom-1 right-0 flex h-8 w-8 items-center justify-center rounded-full border border-[#d3d8df] bg-[#f2f4f7] text-[#304057] shadow-sm" title="Đổi ảnh đại diện">
                      <Camera size={17} strokeWidth={1.7} />
                    </button>
                  </div>
                  <div className="flex min-w-0 items-center gap-3 pt-1">
                    <h3 className="truncate text-[22px] font-semibold text-[#081c36]">{userName}</h3>
                    <button type="button" onClick={beginProfileEdit} className="flex h-8 w-8 items-center justify-center rounded-md text-[#304057] hover:bg-[#edf1f5]" title="Chỉnh sửa tên">
                      <Pencil size={18} strokeWidth={1.7} />
                    </button>
                  </div>
                </div>

                <div className="px-5 py-5">
                  <h3 className="mb-5 text-[20px] font-semibold text-[#081c36]">Thông tin cá nhân</h3>
                  <div className="grid grid-cols-[112px_1fr] gap-y-4 text-[16px]">
                    <div className="text-[#667085]">Giới tính</div>
                    <div className="text-[#1f2f46]">{accountGender}</div>
                    <div className="text-[#667085]">Ngày sinh</div>
                    <div className="text-[#1f2f46]">{accountDateOfBirth}</div>
                    <div className="text-[#667085]">Điện thoại</div>
                    <div className="text-[#1f2f46]">{formatPhone(accountPhone)}</div>
                  </div>
                </div>

                <div className="border-t border-[#eef0f4] px-5 py-3">
                  <button
                    type="button"
                    onClick={beginProfileEdit}
                    className="flex h-10 w-full items-center justify-center gap-2 rounded-[3px] bg-[#eef1f5] text-[18px] font-semibold text-[#081c36] hover:bg-[#e3e7ed]"
                  >
                    <Pencil size={20} strokeWidth={1.8} />
                    Cập nhật
                  </button>
                </div>
              </>
            ) : profileMode === 'edit' ? (
              <>
                <div className="px-5 py-5">
                  <div className="space-y-4">
                    <label className="block">
                      <span className="mb-1.5 block text-[14px] font-medium text-[#344054]">Tên hiển thị</span>
                      <input
                        value={draftName}
                        onChange={(event) => setDraftName(event.target.value)}
                        className="h-10 w-full rounded-md border border-[#d0d5dd] px-3 text-[15px] text-[#081c36] outline-none focus:border-[#0068ff] focus:ring-2 focus:ring-[#0068ff]/15"
                      />
                    </label>

                    <div>
                      <span className="mb-2 block text-[14px] font-medium text-[#344054]">Giới tính</span>
                      <div className="flex gap-5 text-[15px] text-[#081c36]">
                        {['', 'Nam', 'Nữ', 'Khác'].map(gender => (
                          <label key={gender} className="flex items-center gap-2">
                            <input
                              type="radio"
                              name="gender"
                              value={gender}
                              checked={draftGender === gender}
                              onChange={() => setDraftGender(gender)}
                              className="h-4 w-4 accent-[#0068ff]"
                            />
                            {gender || UNSET_PROFILE_VALUE}
                          </label>
                        ))}
                      </div>
                    </div>

                    <label className="block">
                      <span className="mb-1.5 block text-[14px] font-medium text-[#344054]">Ngày sinh</span>
                      <input
                        type="date"
                        value={draftBirthday}
                        onChange={(event) => setDraftBirthday(event.target.value)}
                        className="h-10 w-full rounded-md border border-[#d0d5dd] px-3 text-[15px] text-[#081c36] outline-none focus:border-[#0068ff] focus:ring-2 focus:ring-[#0068ff]/15"
                      />
                    </label>

                    <label className="block">
                      <span className="mb-1.5 block text-[14px] font-medium text-[#344054]">Điện thoại</span>
                      <input
                        value={formatPhone(accountPhone)}
                        disabled
                        className="h-10 w-full rounded-md border border-[#d0d5dd] bg-[#f5f6f8] px-3 text-[15px] text-[#667085]"
                      />
                    </label>
                  </div>
                </div>

                {updateError && (
                  <div className="px-5 pb-2 text-[13px] text-red-500">{updateError}</div>
                )}

                <div className="flex justify-end gap-3 border-t border-[#eef0f4] px-5 py-3">
                  <button
                    type="button"
                    onClick={() => setProfileMode('view')}
                    className="h-10 rounded-md px-5 text-[15px] font-medium text-[#081c36] hover:bg-[#f0f2f5]"
                  >
                    Hủy
                  </button>
                  <button
                    type="button"
                    onClick={applyProfileUpdate}
                    disabled={isUpdating}
                    className="h-10 rounded-md bg-[#0068ff] px-6 text-[15px] font-semibold text-white hover:bg-[#0057d9] disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {isUpdating ? 'Đang cập nhật...' : 'Cập nhật'}
                  </button>
                </div>
              </>
            ) : avatarPreview ? (
              <div className="flex flex-col flex-1 h-[440px]">
                <div className="flex-1 px-5 py-8 flex flex-col items-center justify-center">
                  <div className="relative w-[280px] h-[280px] overflow-hidden flex items-center justify-center rounded-full border-4 border-[#eef0f4] shadow-sm">
                    <img src={avatarPreview} alt="Preview" className="w-full h-full object-cover" />
                  </div>
                </div>
                
                {updateError && (
                  <div className="px-5 pb-2 text-[13px] text-red-500 text-center">{updateError}</div>
                )}
                
                <div className="flex justify-end gap-3 border-t border-[#eef0f4] px-5 py-3 mt-auto">
                  <button
                    type="button"
                    onClick={() => setAvatarPreview(null)}
                    className="h-10 rounded-md bg-[#f0f2f5] px-6 text-[15px] font-medium text-[#081c36] hover:bg-[#e3e7ed]"
                  >
                    Hủy
                  </button>
                  <button
                    type="button"
                    onClick={applyAvatarUpdate}
                    disabled={isUpdating}
                    className="h-10 rounded-md bg-[#0068ff] px-6 text-[15px] font-semibold text-white hover:bg-[#0057d9] disabled:opacity-60"
                  >
                    {isUpdating ? 'Đang cập nhật...' : 'Cập nhật'}
                  </button>
                </div>
              </div>
            ) : (
              <div className="px-5 pt-4 pb-12 min-h-[440px]">
                <input
                  type="file"
                  ref={fileInputRef}
                  className="hidden"
                  accept="image/png, image/jpeg, image/jpg"
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) {
                      setAvatarFile(file);
                      setAvatarPreview(URL.createObjectURL(file));
                    }
                  }}
                />
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  className="flex w-full items-center justify-center gap-2 rounded-[4px] bg-[#e5efff] py-2.5 text-[14px] font-medium text-[#0068ff] transition-colors hover:bg-[#d6e5ff]"
                >
                  <Image size={18} strokeWidth={2} />
                  Tải lên từ máy tính
                </button>
                
                <h3 className="mt-6 text-[15px] font-medium text-[#081c36]">
                  Ảnh đại diện của tôi
                </h3>
                
                <div className="mt-14 flex flex-col items-center justify-center">
                  <div className="flex h-[100px] w-[100px] items-center justify-center rounded-full bg-[#f2f4f7]">
                    <User size={52} strokeWidth={2} fill="#b0b9c6" className="text-[#b0b9c6] mt-2" />
                  </div>
                  <p className="mt-5 text-[14px] text-[#304057]">
                    Bạn chưa cập nhật ảnh đại diện nào
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </>
  );
}
