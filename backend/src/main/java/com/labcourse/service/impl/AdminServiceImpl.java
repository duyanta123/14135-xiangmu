package com.labcourse.service.impl;

import com.labcourse.entity.Admin;
import com.labcourse.exception.AccountLockedException;
import com.labcourse.repository.AdminRepository;
import com.labcourse.service.AdminService;
import com.labcourse.service.LoginAttemptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@SuppressWarnings("null")
public class AdminServiceImpl implements AdminService {

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Override
    public Admin login(String username, String password) {
        String key = "admin:" + username;

        LoginAttemptService.LoginResult checkResult = loginAttemptService.checkLoginAttempt(key);
        if (!checkResult.isAllowed()) {
            throw new AccountLockedException(
                    "账号已被锁定，请" + checkResult.getRemainingLockMinutes() + "分钟后再试",
                    checkResult.getRemainingLockMinutes());
        }

        Optional<Admin> adminOpt = adminRepository.findByUsername(username);
        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();
            if (passwordEncoder.matches(password, admin.getPassword())) {
                loginAttemptService.resetAttempts(key);
                return admin;
            }
        }

        loginAttemptService.recordFailedAttempt(key);
        return null;
    }

    @Override
    public List<Admin> list() {
        return adminRepository.findAll();
    }

    @Override
    public boolean save(Admin admin) {
        admin.setPassword(passwordEncoder.encode(admin.getPassword()));
        adminRepository.save(admin);
        return true;
    }

    @Override
    public boolean updateById(Admin admin) {
        Optional<Admin> existingOpt = adminRepository.findById(admin.getId());
        if (existingOpt.isPresent()) {
            Admin existing = existingOpt.get();
            existing.setUsername(admin.getUsername());
            if (admin.getPassword() != null && !admin.getPassword().isEmpty()) {
                existing.setPassword(passwordEncoder.encode(admin.getPassword()));
            }
            adminRepository.save(existing);
            return true;
        }
        return false;
    }

    @Override
    public boolean removeById(Long id) {
        adminRepository.deleteById(id);
        return true;
    }
}
