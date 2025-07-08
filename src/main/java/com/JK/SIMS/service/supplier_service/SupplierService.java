package com.JK.SIMS.service.supplier_service;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.supplier.Supplier;
import com.JK.SIMS.models.supplier.SupplierRequest;
import com.JK.SIMS.models.supplier.SupplierResponse;
import com.JK.SIMS.repository.supplier_repo.SupplierRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class SupplierService {
    private final SupplierRepository supplierRepository;

    public SupplierService(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @Transactional
    public ApiResponse createSupplier(SupplierRequest request) {
        // Basic check for unique name/email if desired, though DB unique constraint will also catch it
        if (supplierRepository.findByName(request.getName()).isPresent()) {
            throw new RuntimeException("Supplier with name " + request.getName() + " already exists.");
        }
        if (request.getEmail() != null && supplierRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Supplier with email " + request.getEmail() + " already exists.");
        }

        validateSupplierRequest(request);

        Supplier supplier = new Supplier();
        supplier.setName(request.getName());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setEmail(request.getEmail());
        supplier.setPhone(request.getPhone());
        supplier.setAddress(request.getAddress());

        supplierRepository.save(supplier);
        return new ApiResponse(true, "Supplier created successfully!");
    }

    private void validateSupplierRequest(SupplierRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (request.getContactPerson() == null || request.getContactPerson().trim().isEmpty()) {
            throw new IllegalArgumentException("Contact person is required");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (request.getPhone() == null || request.getPhone().trim().isEmpty()) {
            throw new IllegalArgumentException("Phone is required");
        }
        if (request.getAddress() == null || request.getAddress().trim().isEmpty()) {
            throw new IllegalArgumentException("Address is required");
        }
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplierById(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Supplier not found with ID: " + id));
        return new SupplierResponse(supplier);
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> getAllSuppliers() {
        return supplierRepository.findAll().stream()
                .map(SupplierResponse::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public ApiResponse updateSupplier(Long id, SupplierRequest request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Supplier not found with ID: " + id));

        if (request.getName() != null) {
            supplier.setName(request.getName());
        }
        if (request.getContactPerson() != null) {
            supplier.setContactPerson(request.getContactPerson());
        }
        if (request.getEmail() != null) {
            supplier.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            supplier.setPhone(request.getPhone());
        }
        if (request.getAddress() != null) {
            supplier.setAddress(request.getAddress());
        }

        supplierRepository.save(supplier);
        return new ApiResponse(true, supplier.getName() + " updated successfully.");
    }

    @Transactional
    public void deleteSupplier(Long id) {
        if (!supplierRepository.existsById(id)) {
            throw new NoSuchElementException("Supplier not found with ID: " + id);
        }
        supplierRepository.deleteById(id);
    }

    // Helper method for internal use
    @Transactional(readOnly = true)
    public Supplier getSupplierEntityById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Supplier not found with ID: " + id));
    }
}
