package com.JK.SIMS.service.userManagement_service;

import com.JK.SIMS.models.UM_models.UserPrincipal;
import com.JK.SIMS.models.UM_models.Users;
import com.JK.SIMS.repository.UserManagement_repo.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Autowired
    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        Users user = userRepository.findByUsernameOrEmail(login)
                .orElseThrow(() -> new UsernameNotFoundException("No User Found: " + login));

        return new UserPrincipal(user);
    }
}

