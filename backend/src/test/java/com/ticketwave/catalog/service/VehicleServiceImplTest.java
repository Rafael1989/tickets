package com.ticketwave.catalog.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.catalog.dto.VehicleRequest;
import com.ticketwave.catalog.dto.VehicleResponse;
import com.ticketwave.catalog.model.RouteType;
import com.ticketwave.catalog.entity.Vehicle;
import com.ticketwave.catalog.mapper.VehicleMapper;
import com.ticketwave.catalog.repository.VehicleRepository;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VehicleServiceImplTest {

    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VehicleMapper vehicleMapper;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private VehicleServiceImpl vehicleService;

    @Test
    void createVehicle_resolvesOperatorFromUsernameAndSavesAndAudits() {
        User operator = User.builder().id(1L).username("operator1").build();
        VehicleRequest request = new VehicleRequest(RouteType.BUS, "BUS-1234", 45, "Volvo 9800");
        Vehicle entity = Vehicle.builder().operator(operator).type(RouteType.BUS).identifier("BUS-1234").build();
        Vehicle saved = Vehicle.builder().id(5L).operator(operator).type(RouteType.BUS).identifier("BUS-1234").build();
        VehicleResponse response = new VehicleResponse(5L, 1L, RouteType.BUS, "BUS-1234", 45, "Volvo 9800");

        given(userRepository.findByUsername("operator1")).willReturn(Optional.of(operator));
        given(vehicleMapper.toEntity(request, operator)).willReturn(entity);
        given(vehicleRepository.save(entity)).willReturn(saved);
        given(vehicleMapper.toResponse(saved)).willReturn(response);

        VehicleResponse result = vehicleService.createVehicle("operator1", request);

        assertThat(result).isEqualTo(response);
        verify(auditService).record(eq("operator1"), eq("VEHICLE_CREATED"), eq("VEHICLE"), any(), any());
    }

    @Test
    void createVehicle_whenOperatorMissing_throwsUserNotFoundException() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());
        VehicleRequest request = new VehicleRequest(RouteType.BUS, "BUS-1234", 45, null);

        assertThatThrownBy(() -> vehicleService.createVehicle("ghost", request))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void listMyVehicles_returnsVehiclesOwnedByTheOperator() {
        User operator = User.builder().id(1L).username("operator1").build();
        Vehicle vehicle = Vehicle.builder().id(5L).operator(operator).type(RouteType.BUS).identifier("BUS-1234").build();
        VehicleResponse response = new VehicleResponse(5L, 1L, RouteType.BUS, "BUS-1234", 45, null);

        given(userRepository.findByUsername("operator1")).willReturn(Optional.of(operator));
        given(vehicleRepository.findByOperatorId(1L)).willReturn(List.of(vehicle));
        given(vehicleMapper.toResponse(vehicle)).willReturn(response);

        List<VehicleResponse> result = vehicleService.listMyVehicles("operator1");

        assertThat(result).containsExactly(response);
    }

    @Test
    void listMyVehicles_whenOperatorMissing_throwsUserNotFoundException() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.listMyVehicles("ghost"))
                .isInstanceOf(UserNotFoundException.class);
    }
}
