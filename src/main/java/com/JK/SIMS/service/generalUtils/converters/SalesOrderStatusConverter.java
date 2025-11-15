package com.JK.SIMS.service.generalUtils.converters;

import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class SalesOrderStatusConverter implements Converter<String, SalesOrderStatus> {

    @Override
    public SalesOrderStatus convert(String source) {
        try{
            return SalesOrderStatus.valueOf(source.trim().toUpperCase());
        } catch (IllegalArgumentException e){
            throw new ValidationException("Invalid status: " + source);
        }
    }
}
