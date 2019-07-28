package com.scurab.java.ftpleecher.tools;

import org.apache.commons.net.ftp.FTPReply;

import java.lang.reflect.Field;

/**
 * Created with IntelliJ IDEA.
 * User: Joe Scurab
 * Date: 11.2.13
 * Time: 20:43
 * To change this template use File | Settings | File Templates.
 */
public class TextUtils {

    public static boolean isNullOrEmpty(String v) {
        return v == null || v.length() == 0;
    }

    /**
     * Help method for translating number ftp codes to readable string<br/>
     * Uses reflection
     *
     * @param value
     * @return
     */
    public static String getFtpCodeName(int value) {
        Field[] fields = FTPReply.class.getDeclaredFields();
        for (Field f : fields) {
            if (f.getType() == int.class) {
                try {
                    if (value == f.getInt(null)) {
                        return f.getName();
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return "";
    }
}
