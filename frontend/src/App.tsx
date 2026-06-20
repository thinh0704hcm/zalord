import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/account/Login';
import Signup from './pages/account/Signup';
import ChatLayout from './pages/chat/ChatLayout';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/account/login" element={<Login />} />
        <Route path="/account/signup" element={<Signup />} />
        
        {/* Main App Layout */}
        <Route path="/chat" element={<ChatLayout />} />
        <Route path="/" element={<Navigate to="/chat" replace />} />
        
        {/* Redirect any unknown route to chat for now */}
        <Route path="*" element={<Navigate to="/chat" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
