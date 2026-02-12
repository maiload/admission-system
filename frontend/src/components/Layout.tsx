import { Link, Outlet } from 'react-router-dom';
import korailLogo from '../assets/korail-logo.png';

export default function Layout() {
  const snsItems = ['youtube', 'facebook', 'instagram', 'blog', 'x'] as const;

  return (
    <div className="min-h-screen bg-ktx-gray flex flex-col">
      {/* Header */}
      <header>
        {/* Top Nav Bar */}
        <div className="bg-[#014B63] text-white">
          <div className="max-w-6xl mx-auto px-6 py-6 flex items-center justify-between">
            <Link to="/" className="flex items-center gap-2">
              <img src={korailLogo} alt="KORAIL" className="h-8 brightness-0 invert" />
              <span className="text-sm font-medium opacity-90">승차권예매</span>
            </Link>
            <div className="flex items-center gap-6 ml-auto">
              <div className="hidden sm:flex items-center gap-8 text-sm font-semibold">
                <span className="cursor-pointer hover:opacity-80">승차권</span>
                <span className="cursor-pointer hover:opacity-80">철도역·열차</span>
                <span className="cursor-pointer hover:opacity-80">고객서비스</span>
                <span className="cursor-pointer hover:opacity-80">코레일멤버십</span>
              </div>
              <button className="text-white text-2xl leading-none cursor-pointer hover:opacity-80">
                ≡
              </button>
            </div>
          </div>
        </div>
        {/* Sub Header */}
        <div className="bg-[#098EB9] text-white">
          <div className="max-w-6xl mx-auto px-6 py-8">
            <h1 className="text-xl font-bold text-center">승차권 예매</h1>
          </div>
        </div>
      </header>

      {/* Content */}
      <main className="flex-1 max-w-4xl w-full mx-auto px-4 py-6">
        <Outlet />
      </main>

      {/* Footer */}
      <footer>
        {/* Footer Links */}
        <div className="bg-[#4a4a4a] text-white">
          <div className="max-w-6xl mx-auto px-6 py-3 flex flex-wrap items-center justify-between gap-4">
            <div className="flex flex-wrap items-center gap-2 text-xs">
              <span className="cursor-pointer hover:underline">이용약관</span>
              <span className="text-white/40">•</span>
              <span className="cursor-pointer hover:underline">여객운송약관 및 부속약관</span>
              <span className="text-white/40">•</span>
              <span className="text-green-400 font-bold cursor-pointer hover:underline">개인정보처리방침</span>
              <span className="text-white/40">•</span>
              <span className="cursor-pointer hover:underline">이메일무단수집거부</span>
              <span className="text-white/40">•</span>
              <span className="cursor-pointer hover:underline">저작권정책</span>
              <span className="text-white/40">•</span>
              <span className="cursor-pointer hover:underline">지원 브라우저 안내</span>
            </div>
            <div className="flex items-center gap-3">
              {/* SNS Icons */}
              {snsItems.map((sns) => (
                <span
                  key={sns}
                  className="w-8 h-8 rounded-full bg-white/20 flex items-center justify-center text-xs font-bold cursor-pointer hover:bg-white/30"
                >
                  {sns === 'youtube' && '▶'}
                  {sns === 'facebook' && 'f'}
                  {sns === 'instagram' && '○'}
                  {sns === 'blog' && 'b'}
                  {sns === 'x' && '✕'}
                </span>
              ))}
            </div>
          </div>
        </div>
        {/* Footer Info */}
        <div className="bg-[#4a4a4a] border-t border-white/10">
          <div className="max-w-6xl mx-auto px-6 py-5 flex flex-wrap justify-between gap-4">
            <div className="text-xs leading-relaxed text-white/60 space-y-1">
              <p>
                상호 : 한국철도공사 ｜ 사업자등록 : 314-82-10024 ｜ 통신판매업신고 : 대전 동구 - 0233호
              </p>
              <p>
                34618 대전광역시 동구 중앙로 240 ｜ 대표전화 : 1588-7788 ｜ 팩스번호 02-361-8385 ｜ 대표이메일 : service@korail.com
              </p>
              <p className="text-[11px] text-white/40 mt-2">
                COPYRIGHT(C) KOREA RAILROAD. ALL RIGHTS RESERVED.
              </p>
            </div>
            <div className="flex items-start">
              <button className="border border-white/30 rounded px-4 py-2 text-xs text-white/70 cursor-pointer hover:bg-white/10 flex items-center gap-1">
                관련 사이트 <span className="text-[10px]">▼</span>
              </button>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
