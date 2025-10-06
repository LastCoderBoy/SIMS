package com.JK.SIMS.service.utilities;

import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.PM_models.ProductCategories;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ProductCategoriesConverter implements Converter<String, ProductCategories> {

    @Override
    public ProductCategories convert(String source) {
        try {
            return ProductCategories.valueOf(source.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid category: " + source);
        }
    }
}
