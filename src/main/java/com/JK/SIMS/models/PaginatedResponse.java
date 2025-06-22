package com.JK.SIMS.models;

import lombok.Data;

import java.util.List;

@Data
public class PaginatedResponse<T> {
    private List<T> content;
    private int totalPages;
    private long totalElements;
}
