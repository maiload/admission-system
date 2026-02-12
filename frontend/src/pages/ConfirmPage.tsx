import { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useBookingStore } from '../stores/bookingStore';
import { confirmBooking } from '../api/client';
import korailLogo from '../assets/korail-logo.png';

export default function ConfirmPage() {
  const navigate = useNavigate();
  const schedule = useBookingStore((s) => s.schedule);
  const holdGroupId = useBookingStore((s) => s.holdGroupId);
  const expiresAt = useBookingStore((s) => s.expiresAt);
  const selectedSeatIds = useBookingStore((s) => s.selectedSeatIds);
  const seats = useBookingStore((s) => s.seats);
  const selectedSeatLabels = useBookingStore((s) => s.selectedSeatLabels);
  const setConfirmationId = useBookingStore((s) => s.setConfirmationId);

  const [remainSec, setRemainSec] = useState(0);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState('');
  const timerRef = useRef<ReturnType<typeof setInterval>>(null);

  useEffect(() => {
    if (!schedule || !holdGroupId) {
      navigate('/', { replace: true });
      return;
    }

    const expMs = new Date(expiresAt).getTime();
    const updateRemain = () => {
      const diff = Math.max(0, Math.floor((expMs - Date.now()) / 1000));
      setRemainSec(diff);
      if (diff <= 0) {
        if (timerRef.current) clearInterval(timerRef.current);
        // 홀드 클린업(2초 간격) 후 좌석이 AVAILABLE로 복구되도록 대기
        setTimeout(() => navigate('/seats', { replace: true }), 3000);
      }
    };
    updateRemain();
    timerRef.current = setInterval(updateRemain, 1000);
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [schedule, holdGroupId, expiresAt, navigate]);

  const handleConfirm = async () => {
    if (!holdGroupId) return;
    setConfirming(true);
    setError('');
    try {
      const res = await confirmBooking(holdGroupId);
      setConfirmationId(res.confirmationId);
      navigate('/complete', { replace: true });
    } catch {
      setError('예매 확정에 실패했습니다. 다시 시도해주세요.');
      setConfirming(false);
    }
  };

  if (!schedule) return null;

  const formatTimer = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  const expired = remainSec <= 0;
  const urgent = remainSec > 0 && remainSec <= 30;
  const fallbackLabels = selectedSeatIds.map((id) => {
    const seat = seats.find((s) => s.seatId === id);
    return seat ? seat.label : id.slice(0, 8);
  });
  const displaySeatLabels = selectedSeatLabels.length > 0 ? selectedSeatLabels : fallbackLabels;

  const totalPrice = schedule.price * selectedSeatIds.length;

  return (
    <div className="max-w-md mx-auto">
      {/* Page title */}
      <div className="flex items-center gap-3 mb-4">
        <button
          onClick={() => navigate('/', { replace: true })}
          className="text-gray-500 text-xl cursor-pointer hover:text-gray-700"
        >
          ‹
        </button>
        <h1 className="text-lg font-bold text-ktx-navy">선택한 승차권</h1>
      </div>

      {/* Timer notice */}
      <div className={`rounded-lg p-3 mb-5 flex items-start gap-2 text-sm ${expired ? 'bg-red-50 border border-red-200' : urgent ? 'bg-orange-50 border border-orange-200' : 'bg-amber-50 border border-amber-200'}`}>
        <span className="text-amber-500 mt-0.5">ⓘ</span>
        <div>
          <p className={`font-semibold ${expired ? 'text-red-600' : 'text-gray-700'}`}>
            결제 제한 시간: <span className="font-mono">{formatTimer(remainSec)}</span>
          </p>
          {expired
            ? <p className="text-xs text-red-500 mt-1">시간이 만료되었습니다. 좌석 선택 화면으로 이동합니다.</p>
            : <p className="text-xs text-gray-500 mt-1">제한 시간 내에 결제를 완료해 주세요.</p>
          }
        </div>
      </div>

      {/* Ticket card */}
      <div className="bg-white rounded-2xl shadow-lg overflow-hidden mb-6">
        <div className="px-6 py-5">
          {/* Logo + badge */}
          <div className="flex items-center justify-between mb-5">
            <img src={korailLogo} alt="KORAIL" className="h-7" />
            <span className="text-xs border border-gray-300 rounded px-2 py-1 text-gray-500">운임요금</span>
          </div>

          {/* Route - large times */}
          <div className="flex items-center justify-between mb-1">
            <div className="text-center">
              <p className="text-3xl font-bold text-ktx-navy">{schedule.departureTime.slice(0, 5)}</p>
              <p className="text-base text-gray-600 mt-1">{schedule.departure}</p>
            </div>
            <div className="text-gray-300 text-xl px-3">→</div>
            <div className="text-center">
              <p className="text-3xl font-bold text-ktx-navy">{schedule.arrivalTime.slice(0, 5)}</p>
              <p className="text-base text-gray-600 mt-1">{schedule.arrival}</p>
            </div>
          </div>

          {/* Dashed divider */}
          <div className="border-t border-dashed border-gray-200 my-5" />

          {/* Details grid */}
          <div className="grid grid-cols-2 gap-y-4 gap-x-6 text-sm">
            <div>
              <p className="text-gray-400 text-xs">출발일</p>
              <p className="font-semibold text-ktx-navy mt-0.5">{schedule.date}</p>
            </div>
            <div>
              <p className="text-gray-400 text-xs">인원</p>
              <p className="font-semibold text-ktx-navy mt-0.5">성인 {selectedSeatIds.length}</p>
            </div>
            <div>
              <p className="text-gray-400 text-xs">기차번호</p>
              <p className="font-semibold text-ktx-navy mt-0.5">{schedule.trainName}-{schedule.trainNumber}</p>
            </div>
            <div>
              <p className="text-gray-400 text-xs">좌석</p>
              <p className="font-semibold text-ktx-navy mt-0.5">{displaySeatLabels.join(', ')}</p>
            </div>
          </div>

          {/* Dashed divider */}
          <div className="border-t border-dashed border-gray-200 my-5" />

          {/* Price */}
          <div className="text-right">
            <p className="text-2xl font-bold text-ktx-navy">
              {totalPrice.toLocaleString()}<span className="text-base font-normal text-gray-500">원</span>
            </p>
          </div>
        </div>
      </div>

      {/* Error */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-3 mb-4 text-sm text-red-700">
          {error}
        </div>
      )}

      {/* Action buttons */}
      <div className="space-y-3">
        <button
          onClick={handleConfirm}
          disabled={expired || confirming}
          className="w-full bg-ktx-blue hover:bg-ktx-navy disabled:bg-gray-300 text-white font-bold py-4 rounded-xl text-lg transition-colors cursor-pointer disabled:cursor-not-allowed"
        >
          {confirming ? '결제 처리 중...' : expired ? '시간 만료' : '결제하기'}
        </button>
        <button
          onClick={() => navigate('/', { replace: true })}
          className="w-full bg-gray-100 hover:bg-gray-200 text-gray-600 font-semibold py-3 rounded-xl transition-colors cursor-pointer"
        >
          취소
        </button>
      </div>
    </div>
  );
}
