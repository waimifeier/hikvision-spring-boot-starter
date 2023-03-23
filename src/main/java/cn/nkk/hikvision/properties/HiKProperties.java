package cn.nkk.hikvision.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 海康sdk配置类
 */
@ConfigurationProperties(prefix = "hik")
public class HiKProperties {

    /**
     * 海康sdk位置
     */
    private String sdkPath;


    /**
     * m3u8磁盘路径
     */
    private String m3u8Path;


    public String getSdkPath() {
        return sdkPath;
    }

    public void setSdkPath(String sdkPath) {
        this.sdkPath = sdkPath;
    }

    public String getM3u8Path() {
        return m3u8Path;
    }

    public void setM3u8Path(String m3u8Path) {
        this.m3u8Path = m3u8Path;
    }
}
