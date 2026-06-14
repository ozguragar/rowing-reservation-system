export interface User {
  id: number;
  fullName: string;
  email: string;
  role: 'ADMIN' | 'STUDENT' | 'CLUB_MEMBER';
  isFinishedBasicTraining: boolean;
  isOnSchoolTeam: boolean;
  lessonsAttended: number;
  creditBalance?: number;
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
}

export interface Boat {
  id: number;
  sessionId: number;
  type: 'COASTAL' | 'OLYMPIC';
  capacity: number;
  isBasicTrainingBoat: boolean;
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
  createdAt: string;
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
