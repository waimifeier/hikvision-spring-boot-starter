package cn.nkk.hikvision.config;

import cn.nkk.hikvision.properties.HiKProperties;
import cn.nkk.hikvision.sdk.HCNetSDK;
import cn.nkk.hikvision.sdk.PlayCtrl;
import cn.nkk.hikvision.utils.OsSelectUtil;
import com.sun.jna.Native;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.ResourceUtils;

import javax.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 海康威视自动配置类
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({HiKProperties.class})
public class HikVisionAutoConfiguration {

    private final static Logger log = LoggerFactory.getLogger(HikVisionAutoConfiguration.class);

   private HCNetSDK hCNetSDK = null;

   public HikVisionAutoConfiguration(HiKProperties properties){
       if(properties.getSdk_path()==null){
           throw new IllegalArgumentException("请指定sdk类库（注意区分系统版本）");
       }

   }

    @Bean
    public HCNetSDK initSdk(HiKProperties properties){
        synchronized (HCNetSDK.class) {
            String strDllPath = "";
            try {
                //win系统加载库路径
                if (OsSelectUtil.isWindows()) {
                    strDllPath = properties.getSdk_path().startsWith("classpath") ?
                            ResourceUtils.getFile(properties.getSdk_path()+"/HCNetSDK.dll").getPath() : properties.getSdk_path()+"/HCNetSDK.dll";
                }
                //Linux系统加载库路径
                else if (OsSelectUtil.isLinux()){
                    strDllPath = properties.getSdk_path().startsWith("classpath") ?
                            ResourceUtils.getFile(properties.getSdk_path()+"/libhcnetsdk.so").getPath() : properties.getSdk_path()+"/libhcnetsdk.so";
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
    @ConditionalOnWebApplication
    private static PlayCtrl initPlay(HiKProperties properties) {
        PlayCtrl playControl = null;
        synchronized (PlayCtrl.class) {
            String strPlayPath = "";
            try {
                //win系统加载库路径
                if (OsSelectUtil.isWindows())
                    strPlayPath = properties.getSdk_path().startsWith("classpath") ?
                            ResourceUtils.getFile(properties.getSdk_path()+"/PlayCtrl.dll").getPath() : properties.getSdk_path()+"/PlayCtrl.dll";
                //Linux系统加载库路径
                else if (OsSelectUtil.isLinux())
                    strPlayPath = properties.getSdk_path().startsWith("classpath") ?
                            ResourceUtils.getFile(properties.getSdk_path()+"/libPlayCtrl.dll").getPath() : properties.getSdk_path()+"/libPlayCtrl.dll";
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

    @Bean("converterPoolExecutor")
    public ThreadPoolTaskExecutor asyncServiceExecutor(HiKProperties hiKProperties) {

        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        // 设置核心线程数
        HiKProperties.HikPool pool = Optional.ofNullable(hiKProperties.getPool()).orElseGet(HiKProperties.HikPool::new);
        threadPoolTaskExecutor.setCorePoolSize(pool.getCore_pool_size());
        // 设置最大线程数
        threadPoolTaskExecutor.setMaxPoolSize(pool.getMax_pool_size());
        // 配置队列大小
        threadPoolTaskExecutor.setQueueCapacity(pool.getQueue_capacity());
        // 设置线程活跃时间（秒）
        threadPoolTaskExecutor.setKeepAliveSeconds(pool.getKeep_alive_seconds());
        // 设置默认线程名称
        threadPoolTaskExecutor.setThreadNamePrefix("kik-version");
        // 设置拒绝策略
        // CallerRunsPolicy:不在新线程中执行任务，而是由调用者所在的线程来执行
        threadPoolTaskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 执行初始化
        threadPoolTaskExecutor.initialize();
        return threadPoolTaskExecutor;
    }

    @PreDestroy
    public void destroy(){
        //SDK反初始化，释放资源，只需要退出时调用一次
        if (null!=hCNetSDK) {
            hCNetSDK.NET_DVR_Cleanup();
        }
    }
}
