import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/account/Login';
import Signup from './pages/account/Signup';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/account/login" element={<Login />} />
        <Route path="/account/signup" element={<Signup />} />
        
        {/* Placeholder for main app layout */}
        <Route path="/" element={
          <div className="flex h-screen items-center justify-center">
            <div className="text-center">
              <h1 className="text-2xl font-bold mb-4">Zalord Main App (Mock)</h1>
              <a href="/account/login" className="text-[#0068ff] hover:underline">Về trang đăng nhập</a>
            </div>
          </div>
        } />
        
        {/* Redirect any unknown route to login for now */}
        <Route path="*" element={<Navigate to="/account/login" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
