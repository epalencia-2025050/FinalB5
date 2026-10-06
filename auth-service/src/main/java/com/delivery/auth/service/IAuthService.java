package com.delivery.auth.service;

import com.delivery.auth.dto.request.AuthRequest;
import com.delivery.auth.dto.request.RegisterRequest;
import com.delivery.auth.dto.response.AuthResponse;

public interface IAuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(AuthRequest request);
}

