//package com.JK.SIMS;
//
//import java.util.Base64;
//import io.jsonwebtoken.security.Keys;
//import io.jsonwebtoken.SignatureAlgorithm;
//
//public class Main {
//    public static void main(String[] args) {
//        String secret = Base64.getEncoder().encodeToString(
//                Keys.secretKeyFor(SignatureAlgorithm.HS256).getEncoded()
//        );
//        System.out.println("Generated Secret Key: " + secret);
//    }
//}
//
