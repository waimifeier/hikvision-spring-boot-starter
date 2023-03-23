

package cn.nkk.hikvision.utils;


import cn.nkk.hikvision.sdk.HCNetSDK;

import java.util.Calendar;
import java.util.Date;

/**
 * @author
 * @create 2022-03-22-11:13
 */
public class CommonUtil {

    //SDK时间解析
    public static String parseTime(int time)
    {
        int year=(time>>26)+2000;
        int month=(time>>22)&15;
        int day=(time>>17)&31;
        int hour=(time>>12)&31;
        int min=(time>>6)&63;
        int second=(time>>0)&63;
        String sTime=year+"-"+month+"-"+day+"-"+hour+":"+min+":"+second;
        return sTime;
    }

    public static HCNetSDK.NET_DVR_TIME_SEARCH_COND getNvrTimeV50(Date time){
        HCNetSDK.NET_DVR_TIME_SEARCH_COND dvrTime = new HCNetSDK.NET_DVR_TIME_SEARCH_COND();
        Calendar instance = Calendar.getInstance();
        instance.setTime(time);

        dvrTime.wYear = (short) instance.get(Calendar.YEAR);
        dvrTime.byMonth = (byte) (instance.get(Calendar.MONTH) + 1);
        dvrTime.byDay = (byte) instance.get(Calendar.DAY_OF_MONTH);
        dvrTime.byHour = (byte) instance.get(Calendar.HOUR_OF_DAY);
        dvrTime.byMinute = (byte) instance.get(Calendar.MINUTE);
        dvrTime.bySecond = (byte) instance.get(Calendar.SECOND);
        return dvrTime;
    }


    public static HCNetSDK.NET_DVR_TIME getNvrTime(Date time){
        HCNetSDK.NET_DVR_TIME dvrTime = new HCNetSDK.NET_DVR_TIME();
        Calendar instance = Calendar.getInstance();
        instance.setTime(time);
        dvrTime.dwYear = (short) instance.get(Calendar.YEAR);
        dvrTime.dwMonth = (byte) (instance.get(Calendar.MONTH) + 1);
        dvrTime.dwDay = (byte) instance.get(Calendar.DAY_OF_MONTH);
        dvrTime.dwHour = (byte) instance.get(Calendar.HOUR_OF_DAY);
        dvrTime.dwMinute = (byte) instance.get(Calendar.MINUTE);
        dvrTime.dwSecond = (byte) instance.get(Calendar.SECOND);
        return dvrTime;
    }
}
