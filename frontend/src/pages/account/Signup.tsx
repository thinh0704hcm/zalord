import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authService } from '../../services/auth';

export default function Signup() {
  const [displayName, setDisplayName] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSignup = async (e: React.FormEvent) => {
    e.preventDefault();
    console.log("Signup form submitted, version 2");
    setError('');
    setLoading(true);

    try {
      const res = await authService.signup(username, password, displayName);
      console.log('Signup success:', res);
      if (res.token) {
        localStorage.setItem('token', res.token);
        localStorage.setItem('user', JSON.stringify(res.user));
        navigate('/');
      } else {
        // Successful signup but no token returned directly (needs manual login)
        alert('Đăng ký thành công! Vui lòng đăng nhập.');
        navigate('/account/login', { state: { phone: username, password: password } });
      }
    } catch (err: any) {
      setError(err.message || 'Đã có lỗi xảy ra');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex h-screen items-center justify-center bg-gray-50">
      <div className="w-full max-w-md bg-white p-8 rounded-xl shadow-sm border border-gray-100">
        <div className="text-center mb-8">
          <h1 className="text-4xl font-bold text-[#0068ff] mb-2">Zalord</h1>
          <p className="text-gray-600">Đăng ký tài khoản</p>
        </div>

        {error && (
          <div className="mb-4 p-3 bg-red-50 text-red-600 rounded-lg text-sm">
            {error}
          </div>
        )}

        <form onSubmit={handleSignup} className="space-y-4">
          <div>
            <input
              type="text"
              placeholder="Tên hiển thị"
              className="w-full px-4 py-3 rounded-lg border border-gray-300 focus:outline-none focus:border-[#0068ff] focus:ring-1 focus:ring-[#0068ff] transition-colors"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              required
            />
          </div>
          <div>
            <input
              type="text"
              placeholder="Số điện thoại"
              className="w-full px-4 py-3 rounded-lg border border-gray-300 focus:outline-none focus:border-[#0068ff] focus:ring-1 focus:ring-[#0068ff] transition-colors"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </div>
          <div>
            <input
              type="password"
              placeholder="Mật khẩu"
              className="w-full px-4 py-3 rounded-lg border border-gray-300 focus:outline-none focus:border-[#0068ff] focus:ring-1 focus:ring-[#0068ff] transition-colors"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-[#0068ff] text-white py-3 rounded-lg font-medium hover:bg-blue-600 transition-colors disabled:opacity-70 disabled:cursor-not-allowed"
          >
            {loading ? 'Đang đăng ký...' : 'Đăng ký'}
          </button>
        </form>

        <div className="mt-6 text-center text-sm">
          <span className="text-gray-500">Đã có tài khoản? </span>
          <Link to="/account/login" className="text-[#0068ff] font-medium hover:underline">
            Đăng nhập
          </Link>
        </div>
      </div>
    </div>
  );
}
