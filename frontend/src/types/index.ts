export interface User {
  id: number;
  fullName: string;
  email: string;
  role: 'SUPERADMIN' | 'CLUB_ADMIN' | 'TRAINER' | 'MEMBER';
  memberType?: 'STUDENT' | 'RECREATIONAL' | 'DEFAULT';
  clubId?: number;
  clubName?: string;
  isCox?: boolean;
  isFinishedBasicTraining: boolean;
  isOnSchoolTeam: boolean;
  lessonsAttended: number;
  creditBalance?: number;
  featureAvailabilityModule?: boolean;
  featureCancellationRequests?: boolean;
  featureAutoScheduler?: boolean;
  featureShowBookedMembers?: boolean;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}

export interface Session {
  id: number;
  date: string;
  startTime: string;
  endTime: string;
  status: 'DRAFT' | 'APPROVED';
  boats?: Boat[];
  clubName?: string;
}

export interface Boat {
  id: number;
  sessionId: number;
  type: 'COASTAL' | 'OLYMPIC';
  capacity: number;
  isBasicTrainingBoat: boolean;
  hasCoxSeat?: boolean;
  currentBookings: number;
  version: number;
  name: string;
  bookings?: Booking[];
}

export interface Booking {
  id: number;
  userId: number;
  userFullName: string;
  userEmail: string;
  userRole: string;
  boatId: number;
  boatName: string;
  sessionId: number;
  status: 'AUTO_ASSIGNED' | 'MANUAL' | 'CANCELLATION_REQUESTED' | 'CANCELED';
  isCoxSeat?: boolean;
  createdAt: string;
}

export interface Club {
  id: number;
  name: string;
  createdAt: string;
  featureAvailabilityModule: boolean;
  featureCancellationRequests: boolean;
  featureAutoScheduler: boolean;
  featureShowBookedMembers: boolean;
}

export interface LedgerEntry {
  id: number;
  userId: number;
  userFullName: string;
  amount: number;
  reason: string;
  runningBalance: number;
  timestamp: string;
  expirationDate: string | null;
}

export interface AuditLog {
  id: number;
  userEmail: string;
  action: string;
  endpoint: string;
  timestamp: string;
  details: string;
}

export interface Analytics {
  sessionId: number;
  date: string;
  sessionTime: string;
  totalCapacity: number;
  totalBooked: number;
  occupancyPercentage: number;
}
