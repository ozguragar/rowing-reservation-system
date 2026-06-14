package com.rowingclub.backend.dto;

import com.rowingclub.backend.entity.Boat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoatDto {
    private Long id;
    private Long sessionId;
    private String type;
    private Integer capacity;
    private Boolean isBasicTrainingBoat;
    private Integer currentBookings;
    private Boolean hasCoxSeat;
    private Long version;
    private String name;
    private List<BookingDto> bookings;

    public static BoatDto from(Boat boat) {
        return BoatDto.builder()
                .id(boat.getId())
                .sessionId(boat.getSession().getId())
                .type(boat.getType().name())
                .capacity(boat.getCapacity())
                .isBasicTrainingBoat(boat.getIsBasicTrainingBoat())
                .currentBookings(boat.getCurrentBookings())
                .hasCoxSeat(boat.getHasCoxSeat())
                .version(boat.getVersion())
                .name(boat.getName())
                .build();
    }
}
