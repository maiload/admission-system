import { Outlet } from 'react-router-dom';

export default function Layout() {
  return (
    <div className="min-h-screen bg-ktx-gray flex flex-col">
      {/* Header */}
      <header className="bg-ktx-blue text-white shadow-md">
        <div className="max-w-4xl mx-auto px-4 py-3 flex items-center gap-3">
          <div className="flex items-center gap-2">
            <svg width="32" height="32" viewBox="0 0 32 32" fill="none" className="shrink-0">
              <rect width="32" height="32" rx="6" fill="#E63312" />
              <path d="M6 22h20l-2-12H8L6 22z" fill="white" />
              <circle cx="10" cy="24" r="2" fill="white" />
              <circle cx="22" cy="24" r="2" fill="white" />
            </svg>
            <div>
              <span className="text-lg font-bold tracking-wide">KTX</span>
              <span className="text-xs ml-1 opacity-80">승차권 예매</span>
            </div>
          </div>
        </div>
      </header>

      {/* Content */}
      <main className="flex-1 max-w-4xl w-full mx-auto px-4 py-6">
        <Outlet />
      </main>

      {/* Footer */}
      <footer className="bg-ktx-navy text-white/60 text-xs text-center py-3">
        <p>본 사이트는 대용량 트래픽 처리 시스템 데모입니다.</p>
      </footer>
    </div>
  );
}
