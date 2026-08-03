package com.ticketwave.catalog.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.catalog.dto.DriverRequest;
import com.ticketwave.catalog.dto.DriverResponse;
import com.ticketwave.catalog.entity.Driver;
import com.ticketwave.catalog.mapper.DriverMapper;
import com.ticketwave.catalog.repository.DriverRepository;
import com.ticketwave.partner.entity.Partner;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DriverServiceImplTest {

    @Mock
    private DriverRepository driverRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DriverMapper driverMapper;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private DriverServiceImpl driverService;

    @Test
    void createDriver_resolvesOperatorFromUsernameAndSavesAndAudits() {
        User operator = User.builder().id(1L).username("operator1").build();
        DriverRequest request = new DriverRequest("Jane Doe", "LIC-123");
        Driver entity = Driver.builder().operator(operator).fullName("Jane Doe").licenseNumber("LIC-123").build();
        Driver saved = Driver.builder().id(7L).operator(operator).fullName("Jane Doe").licenseNumber("LIC-123").build();
        DriverResponse response = new DriverResponse(7L, 1L, "Jane Doe", "LIC-123");

        given(userRepository.findByUsername("operator1")).willReturn(Optional.of(operator));
        given(driverMapper.toEntity(request, operator)).willReturn(entity);
        given(driverRepository.save(entity)).willReturn(saved);
        given(driverMapper.toResponse(saved)).willReturn(response);

        DriverResponse result = driverService.createDriver("operator1", request);

        assertThat(result).isEqualTo(response);
        verify(auditService).record(eq("operator1"), eq("DRIVER_CREATED"), eq("DRIVER"), any(), any());
    }

    @Test
    void createDriver_whenOperatorMissing_throwsUserNotFoundException() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());
        DriverRequest request = new DriverRequest("Jane Doe", "LIC-123");

        assertThatThrownBy(() -> driverService.createDriver("ghost", request))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void listMyDrivers_returnsDriversOwnedByTheOperator() {
        User operator = User.builder().id(1L).username("operator1").build();
        Driver driver = Driver.builder().id(7L).operator(operator).fullName("Jane Doe").licenseNumber("LIC-123").build();
        DriverResponse response = new DriverResponse(7L, 1L, "Jane Doe", "LIC-123");

        given(userRepository.findByUsername("operator1")).willReturn(Optional.of(operator));
        given(driverRepository.findByOperatorId(1L)).willReturn(List.of(driver));
        given(driverMapper.toResponse(driver)).willReturn(response);

        List<DriverResponse> result = driverService.listMyDrivers("operator1");

        assertThat(result).containsExactly(response);
    }

    @Test
    void listMyDrivers_whenOperatorBelongsToAPartner_listsTheWholePartnerFleet() {
        Partner partner = Partner.builder().id(42L).name("Acme Travel").build();
        User operator = User.builder().id(1L).username("operator1").partner(partner).build();
        // A partner's drivers are shared across all of its operator accounts,
        // so scoping by operator id here would hide colleagues' drivers from
        // an operator who is entitled to see the whole fleet.
        Driver sibling = Driver.builder().id(8L).operator(User.builder().id(2L).build())
                .fullName("John Roe").licenseNumber("LIC-456").build();
        DriverResponse response = new DriverResponse(8L, 2L, "John Roe", "LIC-456");

        given(userRepository.findByUsername("operator1")).willReturn(Optional.of(operator));
        given(driverRepository.findByOperatorPartnerId(42L)).willReturn(List.of(sibling));
        given(driverMapper.toResponse(sibling)).willReturn(response);

        List<DriverResponse> result = driverService.listMyDrivers("operator1");

        assertThat(result).containsExactly(response);
        verify(driverRepository, never()).findByOperatorId(any());
    }

    @Test
    void listMyDrivers_whenOperatorMissing_throwsUserNotFoundException() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> driverService.listMyDrivers("ghost"))
                .isInstanceOf(UserNotFoundException.class);
    }
}
