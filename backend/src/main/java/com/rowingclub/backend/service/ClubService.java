package com.rowingclub.backend.service;

import com.rowingclub.backend.entity.Club;
import com.rowingclub.backend.exception.ResourceNotFoundException;
import com.rowingclub.backend.repository.ClubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClubService {

    private final ClubRepository clubRepository;

    @Transactional(readOnly = true)
    public List<Club> getAllClubs() {
        return clubRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Club getClubById(Long clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new ResourceNotFoundException("Club not found with id: " + clubId));
    }

    @Transactional
    public Club createClub(String name) {
        Club club = Club.builder()
                .name(name)
                .featureAvailabilityModule(true)
                .featureCancellationRequests(true)
                .featureAutoScheduler(true)
                .featureShowBookedMembers(true)
                .build();
        return clubRepository.save(club);
    }

    @Transactional
    public Club updateClubFeatures(Long clubId, Boolean availabilityModule, 
                                    Boolean cancellationRequests, Boolean autoScheduler, 
                                    Boolean showBookedMembers) {
        Club club = getClubById(clubId);
        if (availabilityModule != null) {
            club.setFeatureAvailabilityModule(availabilityModule);
        }
        if (cancellationRequests != null) {
            club.setFeatureCancellationRequests(cancellationRequests);
        }
        if (autoScheduler != null) {
            club.setFeatureAutoScheduler(autoScheduler);
        }
        if (showBookedMembers != null) {
            club.setFeatureShowBookedMembers(showBookedMembers);
        }
        return clubRepository.save(club);
    }
}
