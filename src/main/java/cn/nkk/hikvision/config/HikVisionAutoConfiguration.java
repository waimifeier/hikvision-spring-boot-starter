package cn.nkk.hikvision.config;

import cn.nkk.hikvision.properties.HiKProperties;
import cn.nkk.hikvision.sdk.HCNetSDK;
import cn.nkk.hikvision.sdk.PlayCtrl;
import cn.nkk.hikvision.utils.OsSelectUtil;
import com.sun.jna.Native;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnJndi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ResourceUtils;

import javax.annotation.PreDestroy;

/**
 * 海康威视自动配置类
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({HiKProperties.class})
public class HikVisionAutoConfiguration {

    private final static Logger log = LoggerFactory.getLogger(HikVisionAutoConfiguration.class);

   private HCNetSDK hCNetSDK = null;

   public HikVisionAutoConfiguration(HiKProperties properties){
       if(properties.getSdkPath()!=null){
           throw new RuntimeException("请指定sdk类库（注意区分系统版本）");
       }
   }

    @Bean
    public HCNetSDK initSdk(HiKProperties properties){
        synchronized (HCNetSDK.class) {
            String strDllPath = "";
            try {
                //win系统加载库路径
                if (OsSelectUtil.isWindows()) {
                    strDllPath = properties.getSdkPath().startsWith("classpath") ?
                            ResourceUtils.getFile(properties.getSdkPath()+"/HCNetSDK.dll").getPath() : properties.getSdkPath()+"/HCNetSDK.dll";
                }
                //Linux系统加载库路径
                else if (OsSelectUtil.isLinux()){
                    strDllPath = properties.getSdkPath().startsWith("classpath") ?
                            ResourceUtils.getFile(properties.getSdkPath()+"/libhcnetsdk.so").getPath() : properties.getSdkPath()+"/libhcnetsdk.so";
                }
                hCNetSDK = (HCNetSDK) Native.loadLibrary(strDllPath, HCNetSDK.class);
            } catch (Exception ex) {
                log.error("加载HikSdk失败");
                ex.printStackTrace();
                return null;
            }
        }
        log.info("加载hksdk成功");
        return hCNetSDK;
    }

    @Bean
    private static PlayCtrl initPlay(HiKProperties properties) {
        PlayCtrl playControl = null;
        synchronized (PlayCtrl.class) {
            String strPlayPath = "";
            try {
                //win系统加载库路径
                if (OsSelectUtil.isWindows())
                    strPlayPath = properties.getSdkPath().startsWith("classpath") ?
                            ResourceUtils.getFile(properties.getSdkPath()+"/PlayCtrl.dll").getPath() : properties.getSdkPath()+"/PlayCtrl.dll";
                //Linux系统加载库路径
                else if (OsSelectUtil.isLinux())
                    strPlayPath = properties.getSdkPath().startsWith("classpath") ?
                            ResourceUtils.getFile(properties.getSdkPath()+"/libPlayCtrl.dll").getPath() : properties.getSdkPath()+"/libPlayCtrl.dll";
                playControl = (PlayCtrl) Native.loadLibrary(strPlayPath,PlayCtrl.class);
            } catch (Exception ex) {
                log.error("加载playControl失败}");
                ex.printStackTrace();
                return null;
            }
        }
        log.info("加载PlayCtrl成功");
        return playControl;
    }

    @PreDestroy
    public void destroy(){
        //SDK反初始化，释放资源，只需要退出时调用一次
        if (null!=hCNetSDK) {
            hCNetSDK.NET_DVR_Cleanup();
        }
    }
}
