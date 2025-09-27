package com.JK.SIMS.config.security;

import com.JK.SIMS.exceptionHandler.InvalidTokenException;

public class TokenUtils {

    public static String extractToken(String authorizationHeader){
        if(authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")){
            throw new InvalidTokenException("Invalid token provided");
        }
        return authorizationHeader.substring(7);
    }
}
