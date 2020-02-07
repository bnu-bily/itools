package com.common;

import com.common.bean.SerializeTest;
import com.common.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String []args) {
        SerializeTest test = new SerializeTest();
        test.setCount(9);
        test.setName("hello");
        System.out.println(JacksonUtil.serialize(test));

        SerializeTest test2 = new SerializeTest();
        test2.setCount(93);
        test2.setName("hello2");
        System.out.println(JacksonUtil.serialize(test));

        String str = "{\"name\":\"hello\",\"count\":988}";
        System.out.println(JacksonUtil.deSerialize(str, SerializeTest.class).getCount());


        String strList = "[{\"name\":\"hello\",\"count\":9},{\"name\":\"hello2\",\"count\":93}]";
        System.out.println(JacksonUtil.deSerializeList(strList, SerializeTest.class).get(0).getName());

        String json = "{1:\"oop\",9:\"uui\"}";
        Map<Integer, String> map = JacksonUtil.deSerialize(json, new TypeReference<Map<Integer, String>>() {
        });
        System.out.println(map.get(1));


    }
}
