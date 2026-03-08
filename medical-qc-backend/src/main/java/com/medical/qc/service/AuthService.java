package com.medical.qc.service;

import com.medical.qc.entity.User;

public interface AuthService {
    User login(String username, String password, String role);

    String register(String username, String email, String password, String fullName, String hospital,
            String department, String role);
}
