import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/account/Login';
import Signup from './pages/account/Signup';
import ChatLayout from './pages/chat/ChatLayout';

const canaryMessage = {
  id: 1,
  title: 'Lorem Ipsum',
  body: 'Dolor sit amet, consectetur adipiscing elit.',
  active: true,
};

function CanaryDemo() {
  return (
    <main className="min-h-screen bg-[#101820] px-6 py-12 text-[#f7efe5]">
      <section className="mx-auto max-w-2xl rounded-[2rem] border border-[#f2aa4c]/40 bg-[#16222d] p-8 shadow-2xl shadow-black/30">
        <p className="mb-3 text-xs font-bold uppercase tracking-[0.4em] text-[#f2aa4c]">Canary frontend</p>
        <h1 className="text-4xl font-black tracking-tight">{canaryMessage.title}</h1>
        <p className="mt-4 text-lg text-[#f7efe5]/80">{canaryMessage.body}</p>
        <pre className="mt-8 overflow-x-auto rounded-2xl bg-black/35 p-5 text-sm text-[#f2aa4c]">
          {JSON.stringify(canaryMessage, null, 2)}
        </pre>
      </section>
    </main>
  );
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/account/login" element={<Login />} />
        <Route path="/account/signup" element={<Signup />} />
        <Route path="/chat/canary" element={<CanaryDemo />} />
        
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
