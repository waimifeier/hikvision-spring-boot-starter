<p align="center">
    海康sdk starter
</p>

<p align="center">
    <a href="https://www.apache.org/licenses/LICENSE-2.0">
        <img alt="code style" src="https://img.shields.io/badge/license-Apache%202-4EB1BA.svg?style=flat-square">
   </a>

  <a href="https://jitpack.io/#waimifeier/hikvision-spring-boot-starter">
    <img alt="maven" src="https://jitpack.io/v/waimifeier/hikvision-spring-boot-starter.svg">
  </a>

</p>

# 项目介绍

基于海康威视网络摄像机和NVR录像机的SDK二次开发, 为解决web-sdk播放控件兼容性不友好(只能在低版本浏览器下才能播放)，rtsp回放延迟问题(8s以上延迟)，和多端设备播放兼容等问题。 主要实现了sdk实时预览、回放、抓图等功能, rtsp推流 （无需nginx推流即可播放）


## 快速开始

### maven 依赖

指定仓库地址
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

maven 坐标
```xml
<dependency>
    <groupId>com.github.waimifeier</groupId>
    <artifactId>hikvision-spring-boot-starter</artifactId>
    <version>最新版本</version>
</dependency>
```


### 项目配置

```yaml
# 配置海康威视 sdk 位置
hik:
  sdk-path: classpath:sdk/win
```
## 案例
### rtsp推流
```java
@GetMapping(value = "/video/live.flv",produces = {"video/x-flv;charset=UTF-8"})
public void flvStream(HttpServletResponse response,HttpServletRequest request){

    AsyncContext asyncContext = request.startAsync();
    asyncContext.setTimeout(0);

    // 获取rtsp地址，rtsp回放有延迟
    String rtspUrl = HkUtils.toRtspUrl("ip", "推流端口", "账号", "密码","通道号");
    try {
        HkUtils.rtspToFlv(rtspUrl,asyncContext);
    }catch (Exception e){
        e.printStackTrace();
    } finally {
        try {
            asyncContext.complete();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
```

### sdk推流
```java

/**
 * 实时预览
 */
@GetMapping(value = "/video/live.flv",produces = {"video/x-flv;charset=UTF-8"})
    public void flvStream(HttpServletResponse response,HttpServletRequest request){

        AsyncContext asyncContext = request.startAsync();
        asyncContext.setTimeout(0);

        // sdk抓流，必须登陆
        CameraLogin cameraLogin = HkUtils.doLogin("ip", "端口", "账号", "密码");

        // 开启实时预览 （参数二为通道号，可从登陆信息获取到）
        VideoPreview videoPreview = HkUtils.startRelaPlay(cameraLogin.getUserId(),17);
        PipedOutputStream outputStream = videoPreview.getOutputStream();
        PipedInputStream inputStream = new PipedInputStream();
        try {
            inputStream.connect(outputStream);
            byte[] bytes = new byte[1024];
            int len = 0;
            while ((len = inputStream.read(bytes)) != -1) {
                asyncContext.getResponse().getOutputStream().write(bytes);
                response.setContentType("video/x-flv");
                response.setHeader("Connection", "keep-alive");
                response.setStatus(HttpServletResponse.SC_OK);
                response.flushBuffer();
            }
        }catch (Exception e){
            e.printStackTrace();
        } finally {
            try {
                asyncContext.complete();
                HkUtils.stopRelaPlay(videoPreview.getPlayHandler()); // 记得关闭预览
                if(outputStream!=null) outputStream.close();
                if(inputStream!=null) inputStream.close();
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }
```


```java

/**
 * 回放预览
 */
@GetMapping(value = "/video/live.flv",produces = {"video/x-flv;charset=UTF-8"})
    public void flvStream(HttpServletResponse response,HttpServletRequest request){

        AsyncContext asyncContext = request.startAsync();
        asyncContext.setTimeout(0);

        // sdk抓流，必须登陆
        CameraLogin cameraLogin = HkUtils.doLogin("ip", "端口", "账号", "密码");

        // 开启实时预览 （参数二为通道号，可从登陆信息获取到）
        VideoPreview videoPreview = HkUtils.startBackPlay(cameraLogin.getUserId(),17,"开始时间","结束时间");
        PipedOutputStream outputStream = videoPreview.getOutputStream();
        PipedInputStream inputStream = new PipedInputStream();
        try {
            inputStream.connect(outputStream);
            byte[] bytes = new byte[1024];
            int len = 0;
            while ((len = inputStream.read(bytes)) != -1) {
                asyncContext.getResponse().getOutputStream().write(bytes);
                response.setContentType("video/x-flv");
                response.setHeader("Connection", "keep-alive");
                response.setStatus(HttpServletResponse.SC_OK);
                response.flushBuffer();
            }
        }catch (Exception e){
            e.printStackTrace();
        } finally {
            try {
                asyncContext.complete();
                HkUtils.stopBackPlay(videoPreview.getPlayHandler()); // 记得关闭回放预览
                if(outputStream!=null) outputStream.close();
                if(inputStream!=null) inputStream.close();
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }
```