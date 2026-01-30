import type { TrainSchedule } from '../types';

interface Props {
  schedule: TrainSchedule;
  confirmationId: string;
  seatLabels: string[];
}

export default function MobileTicket({ schedule, confirmationId, seatLabels }: Props) {
  return (
    <div className="bg-white rounded-2xl shadow-xl overflow-hidden max-w-sm mx-auto">
      {/* Top bar */}
      <div className="bg-ktx-blue px-6 py-4 text-white flex items-center justify-between">
        <div className="flex items-center gap-2">
          <svg width="28" height="28" viewBox="0 0 32 32" fill="none">
            <rect width="32" height="32" rx="6" fill="#E63312" />
            <path d="M6 22h20l-2-12H8L6 22z" fill="white" />
            <circle cx="10" cy="24" r="2" fill="white" />
            <circle cx="22" cy="24" r="2" fill="white" />
          </svg>
          <span className="font-bold text-lg">KTX 모바일 승차권</span>
        </div>
        <span className="text-xs bg-white/20 px-2 py-0.5 rounded">확인됨</span>
      </div>

      {/* Train info */}
      <div className="px-6 py-5">
        <div className="flex items-center justify-between mb-4">
          <div className="text-center">
            <p className="text-2xl font-bold text-ktx-navy">{schedule.departure}</p>
            <p className="text-sm text-gray-500 mt-1">{schedule.departureTime}</p>
          </div>
          <div className="flex-1 mx-4 flex flex-col items-center">
            <p className="text-xs text-ktx-red font-semibold mb-1">{schedule.trainName} {schedule.trainNumber}</p>
            <div className="w-full flex items-center">
              <div className="w-2 h-2 rounded-full bg-ktx-blue" />
              <div className="flex-1 border-t-2 border-dashed border-ktx-blue/40 mx-1" />
              <svg width="16" height="16" viewBox="0 0 16 16" fill="#003876">
                <path d="M1 8h12M9 4l4 4-4 4" stroke="#003876" strokeWidth="2" fill="none" />
              </svg>
              <div className="flex-1 border-t-2 border-dashed border-ktx-blue/40 mx-1" />
              <div className="w-2 h-2 rounded-full bg-ktx-red" />
            </div>
          </div>
          <div className="text-center">
            <p className="text-2xl font-bold text-ktx-navy">{schedule.arrival}</p>
            <p className="text-sm text-gray-500 mt-1">{schedule.arrivalTime}</p>
          </div>
        </div>

        {/* Divider */}
        <div className="relative my-4">
          <div className="absolute -left-6 top-1/2 -translate-y-1/2 w-5 h-5 bg-ktx-gray rounded-full" />
          <div className="absolute -right-6 top-1/2 -translate-y-1/2 w-5 h-5 bg-ktx-gray rounded-full" />
          <div className="border-t-2 border-dashed border-gray-200" />
        </div>

        {/* Details */}
        <div className="grid grid-cols-2 gap-3 text-sm">
          <div>
            <p className="text-gray-400">승차일</p>
            <p className="font-semibold text-ktx-navy">{schedule.date}</p>
          </div>
          <div>
            <p className="text-gray-400">좌석</p>
            <p className="font-semibold text-ktx-navy">{seatLabels.join(', ')}</p>
          </div>
          <div>
            <p className="text-gray-400">요금</p>
            <p className="font-semibold text-ktx-navy">
              {(schedule.price * seatLabels.length).toLocaleString()}원
            </p>
          </div>
          <div>
            <p className="text-gray-400">예약번호</p>
            <p className="font-semibold text-ktx-red text-xs break-all">
              {confirmationId.substring(0, 12).toUpperCase()}
            </p>
          </div>
        </div>
      </div>

      {/* Barcode area */}
      <div className="px-6 pb-6">
        <div className="bg-gray-50 rounded-lg p-4 flex flex-col items-center">
          {/* Fake barcode */}
          <div className="flex gap-px mb-2">
            {Array.from({ length: 40 }, (_, i) => (
              <div
                key={i}
                className="bg-ktx-navy"
                style={{
                  width: Math.random() > 0.5 ? 2 : 1,
                  height: 40,
                }}
              />
            ))}
          </div>
          <p className="text-xs text-gray-400 font-mono">
            {confirmationId.substring(0, 16).toUpperCase()}
          </p>
        </div>
      </div>
    </div>
  );
}
