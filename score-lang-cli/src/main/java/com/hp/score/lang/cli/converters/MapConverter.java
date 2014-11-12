package com.hp.score.lang.cli.converters;

import org.springframework.shell.core.Completion;
import org.springframework.shell.core.Converter;
import org.springframework.shell.core.MethodTarget;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Date: 11/11/2014
 *
 * @author lesant
 */

@Component
public class MapConverter implements Converter<Map<String, String>> {
    @Override
    public boolean supports(Class<?> type, String optionContext) {
        return Map.class.isAssignableFrom(type);
    }

    @Override
    public Map<String, String> convertFromText(String value, Class<?> targetType, String optionContext) {
        String[] values = StringUtils.commaDelimitedListToStringArray(value);
        Map<String, String> map = new HashMap<>();

        for (String v : values) {
            String[] keyValue = StringUtils.delimitedListToStringArray(v, "=");
            if (keyValue.length == 2) {
                map.put(keyValue[0], keyValue[1]);
            } else {
                throw new RuntimeException("Input should be in a key=value comma separated format, e.g. key1=val1,key2=val2 etc.");
            }
        }

        return map;
    }

    @Override
    public boolean getAllPossibleValues(List<Completion> completions, Class<?> targetType, String existingData, String optionContext, MethodTarget target) {
        return true;
    }
}