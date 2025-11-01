package com.JK.SIMS.config.security.utils;

import com.JK.SIMS.exception.InvalidTokenException;

public class TokenUtils {

    public static String extractToken(String authorizationHeader){
        if(authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")){
            throw new InvalidTokenException("Invalid token provided");
        }
        return authorizationHeader.substring(7);
    }
}
