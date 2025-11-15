package com.JK.SIMS.service.generalUtils.converters;

import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.PM_models.ProductCategories;
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
