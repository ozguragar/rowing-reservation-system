package com.rowingclub.backend.dto;

import lombok.Data;

@Data
public class UpdateClubFeaturesRequest {
    private Boolean featureAvailabilityModule;
    private Boolean featureCancellationRequests;
    private Boolean featureAutoScheduler;
    private Boolean featureShowBookedMembers;
}
