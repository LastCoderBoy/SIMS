package com.JK.SIMS.service.utilities;

import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrderStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class PurchaseOrderStatusConverter implements Converter<String, PurchaseOrderStatus> {
    @Override
    public PurchaseOrderStatus convert(String source) {
        try{
            return PurchaseOrderStatus.valueOf(source.trim().toUpperCase());
        } catch (IllegalArgumentException e){
            throw new ValidationException("Invalid status: " + source);
        }
    }
}
