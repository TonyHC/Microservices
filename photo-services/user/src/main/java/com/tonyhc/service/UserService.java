package com.tonyhc.service;

import com.tonyhc.clients.album.AlbumClient;
import com.tonyhc.clients.album.AlbumResponse;
import com.tonyhc.domain.User;
import com.tonyhc.dto.UserDTO;
import com.tonyhc.repository.UserRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AlbumClient albumClient;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AlbumClient albumClient) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.albumClient = albumClient;
    }

    public UserDTO createUser(UserDTO userDetails) {
        userDetails.setUserId(UUID.randomUUID().toString());
        userDetails.setPassword(passwordEncoder.encode(userDetails.getPassword()));

        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);

        User user = modelMapper.map(userDetails, User.class);
        userRepository.save(user);

        return modelMapper.map(user, UserDTO.class);
    }

    @CircuitBreaker(name = "albumService-getUserByUserId", fallbackMethod = "fallbackGetUserByUserId")
    public UserDTO getUserByUserId(String userId) {
        UserDTO userDTO = convertUserToUserDTO(userId);

        List<AlbumResponse> albums = albumClient.getUserAlbums(userId);
        userDTO.setAlbums(albums);

        return userDTO;
    }

    public UserDTO fallbackGetUserByUserId(String userId, Throwable throwable) {
        log.error("Error: " + throwable);

        return convertUserToUserDTO(userId);
    }

    private UserDTO convertUserToUserDTO(String userId) {
        User user = userRepository.findByUserId(userId);

        if (user == null) {
            throw new UsernameNotFoundException("Invalid userId");
        }

        return new ModelMapper().map(user, UserDTO.class);
    }
}
