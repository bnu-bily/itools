package com.common;

import com.common.bean.SerializeTest;
import com.common.utils.JacksonUtil;
import com.google.common.collect.Lists;

import java.util.List;

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

    }
}
