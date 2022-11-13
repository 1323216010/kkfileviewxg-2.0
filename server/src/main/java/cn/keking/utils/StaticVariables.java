package cn.keking.utils;

import cn.keking.config.StaticGetPrivate;

public class StaticVariables {


    public static String path = StaticGetPrivate.getmyAddress() + "/demo/";

    public static String vodAddress = StaticGetPrivate.getvodAddress();

    public static String addPath = vodAddress  + "/files/add/";

    public static String updatePath = vodAddress  + "/files/updateByName/";

    public static String deletePath = vodAddress  + "/files/delete/";

}
