import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { TrainSchedule, QueueProgress, Seat } from '../types';

interface BookingState {
  // selected train
  schedule: TrainSchedule | null;
  setSchedule: (s: TrainSchedule) => void;

  // queue gate
  queueToken: string;
  setQueueToken: (t: string) => void;
  queueProgress: QueueProgress | null;
  setQueueProgress: (p: QueueProgress) => void;
  enterToken: string;
  setEnterToken: (t: string) => void;

  // ticketing core
  seats: Seat[];
  setSeats: (s: Seat[]) => void;
  selectedSeatIds: string[];
  selectedSeatLabels: string[];
  toggleSeat: (id: string) => void;
  setSelectedSeatLabels: (labels: string[]) => void;
  holdGroupId: string;
  setHoldGroupId: (id: string) => void;
  expiresAt: string;
  setExpiresAt: (t: string) => void;
  confirmationId: string;
  setConfirmationId: (id: string) => void;

  // partial reset for re-selecting seats
  clearHoldAndSelection: () => void;

  // reset
  reset: () => void;
}

interface PersistedBookingState {
  schedule: TrainSchedule | null;
}

const initialState = {
  schedule: null,
  queueToken: '',
  queueProgress: null,
  enterToken: '',
  seats: [],
  selectedSeatIds: [],
  selectedSeatLabels: [],
  holdGroupId: '',
  expiresAt: '',
  confirmationId: '',
};

export const useBookingStore = create<BookingState>()(
  persist<BookingState, [], [], PersistedBookingState>(
    (set) => ({
      ...initialState,
      setSchedule: (s) => set({ schedule: s }),
      setQueueToken: (t) => set({ queueToken: t }),
      setQueueProgress: (p) => set({ queueProgress: p }),
      setEnterToken: (t) => set({ enterToken: t }),
      setSeats: (s) => set({ seats: s }),
      toggleSeat: (id) =>
        set((state) => {
          const exists = state.selectedSeatIds.includes(id);
          return {
            selectedSeatIds: exists
              ? state.selectedSeatIds.filter((s) => s !== id)
              : [...state.selectedSeatIds, id],
          };
        }),
      setSelectedSeatLabels: (labels) => set({ selectedSeatLabels: labels }),
      setHoldGroupId: (id) => set({ holdGroupId: id }),
      setExpiresAt: (t) => set({ expiresAt: t }),
      setConfirmationId: (id) => set({ confirmationId: id }),
      clearHoldAndSelection: () => set({ holdGroupId: '', expiresAt: '', selectedSeatIds: [], selectedSeatLabels: [] }),
      reset: () => set(initialState),
    }),
    {
      name: 'booking-session',
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({
        schedule: state.schedule,
      }),
    },
  ),
);
