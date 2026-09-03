package com.main.server.service;

import com.main.server.dto.CarrierDto;
import com.main.server.entity.Carrier;
import com.main.server.repository.CarrierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// The airline reference list. Deliberately tiny — it mirrors AirportService.
//
// It exists rather than the controller calling the repository directly so that
// the entity -> DTO conversion has one home, and so the controller stays a layer
// that only deals in HTTP.
@Service
@RequiredArgsConstructor
public class CarrierService {

    private final CarrierRepository carrierRepository;

    public List<CarrierDto> findAll() {
        return carrierRepository.findAllByOrderByNameAsc()
                .stream()
                .map(CarrierService::toDto)
                .toList();
    }

    private static CarrierDto toDto(Carrier c) {
        return new CarrierDto(c.getIcao(), c.getIata(), c.getName());
    }
}
