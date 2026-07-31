package com.ticketwave.catalog.service;

import com.ticketwave.catalog.dto.RouteRequest;
import com.ticketwave.catalog.dto.RouteResponse;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.mapper.RouteMapper;
import com.ticketwave.catalog.repository.RouteRepository;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RouteServiceImpl implements RouteService {

    private final RouteRepository routeRepository;
    private final UserRepository userRepository;
    private final RouteMapper routeMapper;

    public RouteServiceImpl(RouteRepository routeRepository, UserRepository userRepository, RouteMapper routeMapper) {
        this.routeRepository = routeRepository;
        this.userRepository = userRepository;
        this.routeMapper = routeMapper;
    }

    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional
    public RouteResponse createRoute(String operatorUsername, RouteRequest request) {
        User operator = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new UserNotFoundException(operatorUsername));

        Route route = routeRepository.save(routeMapper.toEntity(request, operator));
        return routeMapper.toResponse(route);
    }

    @Override
    @PreAuthorize("hasRole('OPERATOR')")
    @Transactional(readOnly = true)
    public List<RouteResponse> listMyRoutes(String operatorUsername) {
        User operator = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new UserNotFoundException(operatorUsername));

        return routeRepository.findByOperatorId(operator.getId()).stream()
                .map(routeMapper::toResponse)
                .toList();
    }
}
