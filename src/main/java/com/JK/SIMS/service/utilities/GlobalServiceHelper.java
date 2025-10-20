package com.JK.SIMS.service.utilities;

import com.JK.SIMS.exceptionHandler.ResourceNotFoundException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.config.security.JWTService;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.apache.poi.ss.formula.functions.T;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
@Slf4j
public class GlobalServiceHelper {

    //TODO: Use abstract inheritance for common logic serives

    public final JWTService jwtService;
    @Autowired
    public GlobalServiceHelper(JWTService jwtService) {
        this.jwtService = jwtService;
    }

    public static boolean amongInvalidStatus(ProductStatus status) {
        return status.equals(ProductStatus.RESTRICTED) ||
                status.equals(ProductStatus.ARCHIVED) ||
                status.equals(ProductStatus.DISCONTINUED);
    }

    public static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    public void validatePaginationParameters(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page number cannot be negative");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
    }

    @Transactional(readOnly = true)
    public <T> void validateOrderId(Long orderId, JpaRepository<T, Long> repository, String entityName) {
        if (orderId == null || orderId < 1) {
            throw new ValidationException(entityName + " Order ID must be valid and greater than zero");
        }
        if (!repository.existsById(orderId)) {
            throw new ResourceNotFoundException(entityName + " Order with ID " + orderId + " does not exist");
        }
    }

    // Checks the given input is in the ENUM class or not.
    public static <T extends Enum<T>> boolean isInEnum(String value, Class<T> enumClass) {
        try {
            Enum.valueOf(enumClass, value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static <T extends Enum<T>> boolean isInEnum(Enum<T> value, Class<T> enumClass) {
        if(value == null) return false;
        return enumClass.isInstance(value);
    }

    public static @Nullable String getOptionDateValue(String optionDate) {
        String optionDateValue = null;
        if(optionDate != null && !optionDate.trim().isEmpty()){
            switch (optionDate.trim().toLowerCase()) {
                case "orderdate" -> optionDateValue = "orderDate";
                case "deliverydate" -> optionDateValue = "deliveryDate";
                case "estimateddeliverydate" -> optionDateValue = "estimatedDeliveryDate";
                default -> throw new IllegalArgumentException("Invalid optionDate value provided: " + optionDate);
            }
        }
        return optionDateValue;
    }

    public Pageable preparePageable(int page, int size, String sortBy, String sortDirection) {
        validatePaginationParameters(page, size);
        Sort sort = sortDirection.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        return PageRequest.of(page, size, sort);
    }

}
