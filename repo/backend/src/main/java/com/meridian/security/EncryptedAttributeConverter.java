package com.meridian.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@Converter(autoApply = false)
public class EncryptedAttributeConverter implements AttributeConverter<String, String>, ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext ctx) {
        EncryptedAttributeConverter.applicationContext = ctx;
    }

    private AesEncryptionService getEncryptionService() {
        return applicationContext.getBean(AesEncryptionService.class);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return getEncryptionService().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return getEncryptionService().decrypt(dbData);
    }
}
