package cn.nkk.hikvision.utils;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
 
/**
 * Md5工具包
 */
public final class Md5Utils {
 
    /**
     * Md5加密，返回32的字符串
     *
     * @param str
     * @return
     */
    public static String encrypt32(String str) {
        if (str == null) {
            return null;
        }
        String result = "";
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(str.getBytes("utf-8"));
            byte         b[] = md.digest();
            int          i;
            StringBuffer buf = new StringBuffer("");
            for (int offset = 0; offset < b.length; offset++) {
                i = b[offset];
                if (i < 0) {
                    i += 256;
                }
                if (i < 16) {
                    buf.append("0");
                }
                buf.append(Integer.toHexString(i));
            }
            result = buf.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return result;
    }
 
    /**
     * Md5加密，返回16的字符串(16位的密文就是32位中间的16位)
     *
     * @param str
     * @return
     */
    public static String encrypt16(String str) {
        if (str == null) {
            return null;
        }
        return encrypt32(str).substring(8, 24);
    }
 
}