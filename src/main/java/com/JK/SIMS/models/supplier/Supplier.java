package com.JK.SIMS.models.supplier;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "supplier")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // e.g., "Toys Distributors Inc."

    private String contactPerson; // e.g., "John Doe"

    @Column(unique = true)
    private String email; // e.g., "contact@sims.com"

    private String phone; // e.g., "+1234567890"

    private String address; // e.g., "123 Main St, Anytown, USA"
}
