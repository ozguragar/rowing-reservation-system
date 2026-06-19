package com.rowingclub.backend.service;

import com.rowingclub.backend.dto.BookingDto;
import com.rowingclub.backend.dto.BookingRequest;
import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.BookingStatus;
import com.rowingclub.backend.enums.MemberType;
import com.rowingclub.backend.enums.Role;
import com.rowingclub.backend.enums.SessionStatus;
import com.rowingclub.backend.exception.BusinessException;
import com.rowingclub.backend.exception.ResourceNotFoundException;
import com.rowingclub.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;



@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BoatRepository boatRepository;
    private final RowingSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final AppSettingRepository appSettingRepository;

    @Value("${app.booking.student-booking-hour:16}")
    private int defaultStudentBookingHour;

    private int studentBookingHour() {
        return appSettingRepository.findById("student_booking_hour")
                .map(s -> {
                    try { return Integer.parseInt(s.getSettingValue()); }
                    catch (NumberFormatException e) { return defaultStudentBookingHour; }
                })
                .orElse(defaultStudentBookingHour);
    }

    private boolean allowCancellations() {
        return appSettingRepository.findById("allow_cancellations")
                .map(s -> !"false".equalsIgnoreCase(s.getSettingValue()))
                .orElse(true);
    }

    private boolean allowCancellationsForClub(Club club) {
        if (club != null && !club.getFeatureCancellationRequests()) return false;
        return allowCancellations();
    }

    @Transactional
    public BookingDto bookSeat(String userEmail, BookingRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        RowingSession session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (session.getStatus() != SessionStatus.APPROVED) {
            throw new BusinessException("Session is not approved for booking");
        }

        enforceTimeRestrictions(user, session);
        enforceNextDayOnly(user, session);

        if (bookingRepository.existsByUserIdAndSessionIdAndStatusNot(
                user.getId(), session.getId(), BookingStatus.CANCELED)) {
            throw new BusinessException("You already have a booking in this session");
        }

        Boat boat = boatRepository.findById(request.getBoatId())
                .orElseThrow(() -> new ResourceNotFoundException("Boat not found"));

        if (!boat.getSession().getId().equals(session.getId())) {
            throw new BusinessException("Boat does not belong to this session");
        }

        boolean isCoxSeat = Boolean.TRUE.equals(request.getIsCoxSeat());

        if (isCoxSeat) {
            if (!boat.getHasCoxSeat()) {
                throw new BusinessException("This boat does not have a cox seat");
            }
            if (!user.getIsCox() && user.getRole() != Role.TRAINER) {
                throw new BusinessException("Only cox-eligible users or trainers can book cox seats");
            }
            long coxBookings = bookingRepository.findByBoatIdAndStatusNot(boat.getId(), BookingStatus.CANCELED)
                    .stream().filter(b -> Boolean.TRUE.equals(b.getIsCoxSeat())).count();
            if (coxBookings >= 1) {
                throw new BusinessException("Cox seat is already booked");
            }
        } else {
            if (boat.getCurrentBookings() >= boat.getCapacity()) {
                throw new BusinessException("Boat is fully booked");
            }
        }

        if (!user.getIsFinishedBasicTraining() && !boat.getIsBasicTrainingBoat()) {
            throw new BusinessException("You must complete basic training before booking advanced boats");
        }

        boolean needsCredits = !isCoxSeat;
        if (needsCredits) {
            BigDecimal balance = ledgerService.getBalance(user.getId());
            if (balance.compareTo(BigDecimal.ONE) < 0) {
                throw new BusinessException("Insufficient credits. Please add credits to your account.");
            }
        }

        if (!isCoxSeat) {
            boat.setCurrentBookings(boat.getCurrentBookings() + 1);
            boatRepository.save(boat);
        }

        Booking booking = Booking.builder()
                .user(user)
                .boat(boat)
                .session(session)
                .status(BookingStatus.MANUAL)
                .isCoxSeat(isCoxSeat)
                .build();
        booking = bookingRepository.save(booking);

        if (needsCredits) {
            ledgerService.deductCredit(user.getId(), BigDecimal.ONE, "Booking: " + session.getDate() + " " + session.getStartTime());
        }

        return BookingDto.from(booking);
    }

    @Transactional
    public BookingDto cancelBooking(String userEmail, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        Club club = booking.getSession().getClub();
        if (!allowCancellationsForClub(club)) {
            throw new BusinessException("Cancellation requests are currently disabled");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!booking.getUser().getId().equals(user.getId())) {
            throw new BusinessException("You can only cancel your own bookings");
        }

        if (booking.getStatus() == BookingStatus.CANCELED) {
            throw new BusinessException("Booking is already canceled");
        }

        if (booking.getStatus() == BookingStatus.CANCELLATION_REQUESTED) {
            throw new BusinessException("Cancellation already pending admin approval");
        }

        booking.setStatus(BookingStatus.CANCELLATION_REQUESTED);
        bookingRepository.save(booking);

        return BookingDto.from(booking);
    }

    @Transactional
    public BookingDto approveCancellation(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (booking.getStatus() != BookingStatus.CANCELLATION_REQUESTED) {
            throw new BusinessException("Booking is not awaiting cancellation approval");
        }

        booking.setStatus(BookingStatus.CANCELED);
        bookingRepository.save(booking);

        Boat boat = booking.getBoat();
        boolean isCoxSeat = Boolean.TRUE.equals(booking.getIsCoxSeat());
        if (!isCoxSeat) {
            boat.setCurrentBookings(Math.max(0, boat.getCurrentBookings() - 1));
            boatRepository.save(boat);

            ledgerService.refundCredit(booking.getUser().getId(), BigDecimal.ONE,
                    "Refund: Cancellation approved for " + booking.getSession().getDate());
        }

        return BookingDto.from(booking);
    }

    @Transactional
    public BookingDto denyCancellation(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (booking.getStatus() != BookingStatus.CANCELLATION_REQUESTED) {
            throw new BusinessException("Booking is not awaiting cancellation approval");
        }

        booking.setStatus(BookingStatus.MANUAL);
        bookingRepository.save(booking);

        return BookingDto.from(booking);
    }

    public List<BookingDto> getPendingCancellations() {
        return bookingRepository.findByStatusOrderByCreatedAtAsc(BookingStatus.CANCELLATION_REQUESTED)
                .stream().map(BookingDto::from).toList();
    }

    public List<BookingDto> getPendingCancellations(Long clubId) {
        if (clubId == null) return getPendingCancellations();
        return bookingRepository.findByStatusAndClubIdOrderByCreatedAtAsc(BookingStatus.CANCELLATION_REQUESTED, clubId)
                .stream().map(BookingDto::from).toList();
    }

    public List<BookingDto> getUserBookings(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return bookingRepository.findActiveBookingsByUserId(user.getId(), LocalDate.now(ISTANBUL))
                .stream().map(BookingDto::from).toList();
    }

    /** All non-canceled bookings (past + future) for a user, newest first — for the profile page. */
    public List<BookingDto> getBookingsForUser(Long userId) {
        return bookingRepository.findActiveByUserIdFetchSessionBoat(userId)
                .stream().map(BookingDto::from).toList();
    }

    public List<BookingDto> getBookingsForBoat(Long boatId) {
        return bookingRepository.findByBoatIdAndStatusNot(boatId, BookingStatus.CANCELED)
                .stream().map(BookingDto::from).toList();
    }

    public List<BookingDto> getBookingsForSession(Long sessionId) {
        return bookingRepository.findBySessionId(sessionId)
                .stream().filter(b -> b.getStatus() != BookingStatus.CANCELED)
                .map(BookingDto::from).toList();
    }

    @Transactional
    public BookingDto adminBookUser(Long userId, Long boatId, Long sessionId, Boolean coxSeat) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        RowingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        Boat boat = boatRepository.findById(boatId)
                .orElseThrow(() -> new ResourceNotFoundException("Boat not found"));

        boolean isCoxSeat = Boolean.TRUE.equals(coxSeat);

        if (isCoxSeat) {
            if (!boat.getHasCoxSeat()) {
                throw new BusinessException("Boat does not have a cox seat");
            }
            if (!user.getIsCox() && user.getRole() != Role.TRAINER) {
                throw new BusinessException("Only cox-eligible users or trainers can book cox seats");
            }
            long coxBookings = bookingRepository.findByBoatIdAndStatusNot(boatId, BookingStatus.CANCELED)
                    .stream().filter(b -> Boolean.TRUE.equals(b.getIsCoxSeat())).count();
            if (coxBookings >= 1) {
                throw new BusinessException("Cox seat is already booked on this boat");
            }
        } else {
            if (boat.getCurrentBookings() >= boat.getCapacity()) {
                throw new BusinessException("Boat is fully booked");
            }
        }

        if (bookingRepository.existsByUserIdAndSessionIdAndStatusNot(userId, sessionId, BookingStatus.CANCELED)) {
            throw new BusinessException("User already has a booking in this session");
        }

        if (!isCoxSeat) {
            BigDecimal balance = ledgerService.getBalance(userId);
            if (balance.compareTo(BigDecimal.ONE) < 0) {
                throw new BusinessException(user.getFullName() + " has insufficient credits");
            }
            boat.setCurrentBookings(boat.getCurrentBookings() + 1);
            boatRepository.save(boat);

            ledgerService.deductCredit(userId, BigDecimal.ONE,
                    "Admin booking: " + session.getDate() + " " + session.getStartTime());
        }

        Booking booking = Booking.builder()
                .user(user)
                .boat(boat)
                .session(session)
                .status(BookingStatus.MANUAL)
                .isCoxSeat(isCoxSeat)
                .build();
        booking = bookingRepository.save(booking);

        return BookingDto.from(booking);
    }

    @Transactional
    public void adminRemoveBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (booking.getStatus() == BookingStatus.CANCELED) {
            throw new BusinessException("Booking is already canceled");
        }

        booking.setStatus(BookingStatus.CANCELED);
        bookingRepository.save(booking);

        Boat boat = booking.getBoat();
        boolean isCoxSeat = Boolean.TRUE.equals(booking.getIsCoxSeat());
        if (!isCoxSeat) {
            boat.setCurrentBookings(Math.max(0, boat.getCurrentBookings() - 1));
            boatRepository.save(boat);

            ledgerService.refundCredit(booking.getUser().getId(), BigDecimal.ONE,
                    "Admin refund: Removed from " + booking.getSession().getDate());
        }
    }

    @Transactional
    public BookingDto adminMoveUser(Long userId, Long fromBoatId, Long toBoatId, Boolean requestCoxSeat) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Boat fromBoat = boatRepository.findById(fromBoatId)
                .orElseThrow(() -> new ResourceNotFoundException("Source boat not found"));

        Boat toBoat = boatRepository.findById(toBoatId)
                .orElseThrow(() -> new ResourceNotFoundException("Target boat not found"));

        Booking booking = bookingRepository.findByBoatIdAndStatusNot(fromBoatId, BookingStatus.CANCELED)
                .stream()
                .filter(b -> b.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("User does not have a booking on the source boat"));

        boolean currentlyCox = Boolean.TRUE.equals(booking.getIsCoxSeat());
        boolean targetCox = Boolean.TRUE.equals(requestCoxSeat);

        if (targetCox) {
            if (!toBoat.getHasCoxSeat()) {
                throw new BusinessException("Target boat does not have a cox seat");
            }
            if (!user.getIsCox() && user.getRole() != Role.TRAINER) {
                throw new BusinessException("Only cox-eligible users or trainers can book cox seats");
            }
            long coxBookings = bookingRepository.findByBoatIdAndStatusNot(toBoatId, BookingStatus.CANCELED)
                    .stream().filter(b -> Boolean.TRUE.equals(b.getIsCoxSeat()))
                    .filter(b -> !b.getId().equals(booking.getId()))
                    .count();
            if (coxBookings >= 1) {
                throw new BusinessException("Cox seat is already booked on target boat");
            }
        }

        if (currentlyCox && !targetCox) {
            fromBoat.setCurrentBookings(Math.max(0, fromBoat.getCurrentBookings() - 1));
            toBoat.setCurrentBookings(toBoat.getCurrentBookings() + 1);
        } else if (targetCox && !currentlyCox) {
            fromBoat.setCurrentBookings(Math.max(0, fromBoat.getCurrentBookings() - 1));
        } else if (!targetCox && !currentlyCox) {
            if (toBoat.getCurrentBookings() >= toBoat.getCapacity()) {
                throw new BusinessException("Target boat is fully booked");
            }
            fromBoat.setCurrentBookings(Math.max(0, fromBoat.getCurrentBookings() - 1));
            toBoat.setCurrentBookings(toBoat.getCurrentBookings() + 1);
        }

        boatRepository.save(fromBoat);
        boatRepository.save(toBoat);

        booking.setBoat(toBoat);
        booking.setIsCoxSeat(targetCox);
        bookingRepository.save(booking);

        return BookingDto.from(booking);
    }

    public boolean isShowBookedMembers() {
        return appSettingRepository.findById("show_booked_members")
                .map(s -> "true".equals(s.getSettingValue()))
                .orElse(true);
    }

    public boolean isShowBookedMembers(Long boatId) {
        Boat boat = boatRepository.findById(boatId).orElse(null);
        if (boat != null && boat.getSession().getClub() != null) {
            if (!boat.getSession().getClub().getFeatureShowBookedMembers()) return false;
        }
        return isShowBookedMembers();
    }

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private boolean bookingHourDisabled() {
        return appSettingRepository.findById("booking_hour_disabled")
                .map(s -> "true".equalsIgnoreCase(s.getSettingValue()))
                .orElse(false);
    }

    /**
     * Time-of-day + member-type booking rules:
     * <pre>
     *                    Before 16:00   After 16:00
     *   STUDENT          blocked        allowed (optionally tomorrow-only via toggle)
     *   RECREATIONAL     any session    any session EXCEPT tomorrow's
     *   DEFAULT          any session    any session
     * </pre>
     * STUDENT: Can only book after 16:00, restricted to next-day sessions when toggle is on
     * RECREATIONAL: Can book anytime, blocked from tomorrow's sessions after cutoff
     * DEFAULT: Has no time restrictions
     *
     * The {@code booking_hour_disabled} admin setting bypasses everything.
     */
    private void enforceTimeRestrictions(User user, RowingSession session) {
        if (bookingHourDisabled()) return;
        if (user.getMemberType() == null || user.getMemberType() == MemberType.DEFAULT) return;

        LocalTime now = LocalTime.now(ISTANBUL);
        int hour = studentBookingHour();
        LocalTime cutoff = LocalTime.of(hour, 0);
        boolean afterCutoff = !now.isBefore(cutoff);
        LocalDate tomorrow = LocalDate.now(ISTANBUL).plusDays(1);
        boolean sessionIsTomorrow = session.getDate().equals(tomorrow);

        if (user.getMemberType() == MemberType.STUDENT && !afterCutoff) {
            throw new BusinessException(
                    "Students can only book sessions after " + hour + ":00");
        }

        if (user.getMemberType() == MemberType.RECREATIONAL && afterCutoff && sessionIsTomorrow) {
            throw new BusinessException(
                    "After " + hour + ":00, tomorrow's sessions are reserved for students");
        }
    }

    private void enforceNextDayOnly(User user, RowingSession session) {
        if (user.getMemberType() != MemberType.STUDENT) return;

        boolean nextDayOnly = appSettingRepository.findById("student_next_day_only")
                .map(s -> "true".equals(s.getSettingValue()))
                .orElse(false);

        if (nextDayOnly) {
            LocalDate tomorrow = LocalDate.now(ISTANBUL).plusDays(1);
            if (!session.getDate().equals(tomorrow)) {
                throw new BusinessException("Students can only book sessions for tomorrow when this restriction is active");
            }
        }
    }
}
