package cn.nkk.hikvision.annotation;

import cn.nkk.hikvision.config.HikVisionAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 海康威视开启注解
 * <p>添加到配置类，即可开启</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(HikVisionAutoConfiguration.class)
public @interface EnableHikVision {

    String value();
}
