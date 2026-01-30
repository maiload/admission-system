import { create } from 'zustand';
import type { TrainSchedule, QueueProgress, Seat } from '../types';

interface BookingState {
  // selected train
  schedule: TrainSchedule | null;
  setSchedule: (s: TrainSchedule) => void;

  // queue gate
  syncToken: string;
  setSyncToken: (t: string) => void;
  queueToken: string;
  setQueueToken: (t: string) => void;
  queueProgress: QueueProgress | null;
  setQueueProgress: (p: QueueProgress) => void;
  enterToken: string;
  setEnterToken: (t: string) => void;

  // ticketing core
  coreSessionToken: string;
  setCoreSessionToken: (t: string) => void;
  seats: Seat[];
  setSeats: (s: Seat[]) => void;
  selectedSeatIds: string[];
  toggleSeat: (id: string) => void;
  holdId: string;
  setHoldId: (id: string) => void;
  expiresAt: string;
  setExpiresAt: (t: string) => void;
  confirmationId: string;
  setConfirmationId: (id: string) => void;

  // reset
  reset: () => void;
}

const initialState = {
  schedule: null,
  syncToken: '',
  queueToken: '',
  queueProgress: null,
  enterToken: '',
  coreSessionToken: '',
  seats: [],
  selectedSeatIds: [],
  holdId: '',
  expiresAt: '',
  confirmationId: '',
};

export const useBookingStore = create<BookingState>((set) => ({
  ...initialState,
  setSchedule: (s) => set({ ...initialState, schedule: s }),
  setSyncToken: (t) => set({ syncToken: t }),
  setQueueToken: (t) => set({ queueToken: t }),
  setQueueProgress: (p) => set({ queueProgress: p }),
  setEnterToken: (t) => set({ enterToken: t }),
  setCoreSessionToken: (t) => set({ coreSessionToken: t }),
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
  setHoldId: (id) => set({ holdId: id }),
  setExpiresAt: (t) => set({ expiresAt: t }),
  setConfirmationId: (id) => set({ confirmationId: id }),
  reset: () => set(initialState),
}));
