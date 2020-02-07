package com.common.utils;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.List;



/**
 * @author huaqing
 */
public class JacksonUtil {
    private static final Logger logger = LoggerFactory.getLogger(JacksonUtil.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.disable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);

        mapper.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true);
        mapper.configure(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);

        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public static String serialize(Object data) {
        try {
            return mapper.writeValueAsString(data);
        } catch (Exception e) {
            logger.error("serialize error", e);
            return null;
        }
    }

    public static <T> T deSerialize(String content, Class<T> clazz) {
        try {
            return mapper.readValue(content, clazz);
        } catch (Exception e) {
            logger.error("deSerialize error: {}", content, e);
            return null;
        }
    }

    /**
     *  解析泛型content。用法，例如要解析 Person<E>类型，则可以这样使用：Person<E> p=JSONUtil.deSerialize(json,new
     *  TypeReference<Person<E></E>>()); 再如：解析Map<String,Map<Integer,String>>，则可以这样使用：Map<String,Map<Integer,String>>
     *  m=JSONUtil.deSerialize(json,new TypeReference<Map<String,Map<Integer,String>>>());
     *
     * @param content
     * @param typeReference
     * @param <T>
     * @return
     */
    public static <T> T deSerialize(String content, TypeReference<T> typeReference) {
        try {
            return mapper.readValue(content, typeReference);
        } catch (Exception e) {
            logger.error("deSerialize error: {}", content, e);
            return null;
        }
    }

    public static <T> List<T> deSerializeList(String content, Class<T> elementClasse) {
        JavaType javaType = mapper.getTypeFactory().constructParametricType(List.class, elementClasse);
        List<T> t = Lists.newArrayList();
        try {
            t = mapper.readValue(content, javaType);
        } catch (Exception e) {
            logger.error("deserialize list with java type object error: {}", content, e);
        }
        return t;
    }
}
