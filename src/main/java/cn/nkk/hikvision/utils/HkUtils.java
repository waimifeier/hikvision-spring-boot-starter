package cn.nkk.hikvision.utils;


import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.FIFOCache;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.nkk.hikvision.beans.CameraLogin;
import cn.nkk.hikvision.beans.VideoFile;
import cn.nkk.hikvision.beans.VideoPreview;
import cn.nkk.hikvision.callBack.BackDataCallBack;
import cn.nkk.hikvision.callBack.RealDataCallBack;
import cn.nkk.hikvision.events.FExceptionCallBack;
import cn.nkk.hikvision.factory.FlvConverter;
import cn.nkk.hikvision.factory.M3u8Converter;
import cn.nkk.hikvision.properties.HiKProperties;
import cn.nkk.hikvision.sdk.HCNetSDK;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.servlet.AsyncContext;
import java.io.File;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.*;

/**
 * <p>海康威视工具包</p>
 *
 * @author dlj
 */
public final class HkUtils {

    private final static Logger log = LoggerFactory.getLogger(HkUtils.class);
    private static final HCNetSDK hcNetSDK = SpringContextHolder.getBean(HCNetSDK.class);

    private static final FIFOCache<String, CameraLogin> LOCAL_CACHE = CacheUtil.newFIFOCache(400);


    /**
     * 加载linux系统类库
     */
    static {
        if (OsSelectUtil.isLinux()) {
            HCNetSDK.BYTE_ARRAY ptrByteArray1 = new HCNetSDK.BYTE_ARRAY(256);
            HCNetSDK.BYTE_ARRAY ptrByteArray2 = new HCNetSDK.BYTE_ARRAY(256);
            HiKProperties hiKProperties = SpringContextHolder.getBean(HiKProperties.class);
            String strPathCom = hiKProperties.getSdk_path();

            //这里是库的绝对路径，请根据实际情况修改，注意改路径必须有访问权限
            String strPath1 = strPathCom + "/libcrypto.so";
            String strPath2 = strPathCom + "/libssl.so";

            System.arraycopy(strPath1.getBytes(), 0, ptrByteArray1.byValue, 0, strPath1.length());
            ptrByteArray1.write();
            hcNetSDK.NET_DVR_SetSDKInitCfg(3, ptrByteArray1.getPointer());

            System.arraycopy(strPath2.getBytes(), 0, ptrByteArray2.byValue, 0, strPath2.length());
            ptrByteArray2.write();
            hcNetSDK.NET_DVR_SetSDKInitCfg(4, ptrByteArray2.getPointer());


            HCNetSDK.NET_DVR_LOCAL_SDK_PATH struComPath = new HCNetSDK.NET_DVR_LOCAL_SDK_PATH();
            System.arraycopy(strPathCom.getBytes(), 0, struComPath.sPath, 0, strPathCom.length());
            struComPath.write();
            hcNetSDK.NET_DVR_SetSDKInitCfg(2, struComPath.getPointer());
        }
    }

    /**
     * 是否在线
     *
     * @param serialNumber 设备序列号
     * @return {@link Boolean}
     */
    public static Boolean hasOnline(String serialNumber) {
        try {
            CameraLogin cameraLogin = LOCAL_CACHE.get("serialNumber");
            HCNetSDK.NET_DVR_WORKSTATE_V30 workState = new HCNetSDK.NET_DVR_WORKSTATE_V30();
            boolean result = hcNetSDK.NET_DVR_GetDVRWorkState_V30(cameraLogin.getUserId(), workState);
            if (!result) {
                log.error("获取设备{}状态失败", serialNumber);
                return false;
            }
            log.info("设备状态：{}", workState.dwDeviceStatic);
        } catch (Exception e) {
            log.error("获取设备{}状态失败", e.getMessage());
        }
        return false;
    }


    /**
     * 获取登录配置文件
     *
     * @param serialNumber 序列号
     * @return {@link CameraLogin}
     */
    public static CameraLogin loginProfile(String serialNumber) {
        return LOCAL_CACHE.get(serialNumber);
    }

    /**
     * 设备登陆
     *
     * @param ip       ip
     * @param port     端口
     * @param userName 用户名
     * @param password 密码
     * @return {@link CameraLogin}
     */
    public static CameraLogin doLogin(String ip, String port, String userName, String password) {
        hcNetSDK.NET_DVR_Init();
        hcNetSDK.NET_DVR_SetConnectTime(2000, 1);
        hcNetSDK.NET_DVR_SetReconnect(10000, true);

        HCNetSDK.NET_DVR_LOCAL_GENERAL_CFG struNET_DVR_LOCAL_GENERAL_CFG = new HCNetSDK.NET_DVR_LOCAL_GENERAL_CFG();
        struNET_DVR_LOCAL_GENERAL_CFG.byAlarmJsonPictureSeparate = 1;   //设置JSON透传报警数据和图片分离
        struNET_DVR_LOCAL_GENERAL_CFG.write();
        Pointer pStrNET_DVR_LOCAL_GENERAL_CFG = struNET_DVR_LOCAL_GENERAL_CFG.getPointer();
        hcNetSDK.NET_DVR_SetSDKLocalCfg(17, pStrNET_DVR_LOCAL_GENERAL_CFG);

        HCNetSDK.NET_DVR_DEVICEINFO_V30 m_strDeviceInfo = new HCNetSDK.NET_DVR_DEVICEINFO_V30();//设备信息
        int userId = hcNetSDK.NET_DVR_Login_V30(ip, Short.parseShort(port), userName, password, m_strDeviceInfo);

        //如果注册失败返回-1，获取错误码
        if (userId < 0) {
            log.error("设备{}:{},注册失败：{}", ip, port, hcNetSDK.NET_DVR_GetLastError());
            throw new RuntimeException("登陆失败");
        }

        String serialNumber = new String(m_strDeviceInfo.sSerialNumber).trim();

        int maxIpChannelNum = getChannelNum(m_strDeviceInfo);
        List<CameraLogin.CameraChannel> listChannel = getChannelNumber(userId, maxIpChannelNum, m_strDeviceInfo);
        CameraLogin cameraInfo = new CameraLogin();
        cameraInfo.setChannelNum(maxIpChannelNum);
        cameraInfo.setUserId(userId);
        cameraInfo.setChannels(listChannel);
        cameraInfo.setSerialNumber(serialNumber);

        Pointer pUser = null;

        // 监听异常信息
        HCNetSDK.FExceptionCallBack fExceptionCallBack = new FExceptionCallBack();
        hcNetSDK.NET_DVR_SetExceptionCallBack_V30(0, 0, fExceptionCallBack, pUser);

        log.info("设备{}:{},注册成功,userId={}，设备编号：{},设备数组通道总数:{},", ip, port, userId, serialNumber, maxIpChannelNum);
        LOCAL_CACHE.put(serialNumber, cameraInfo);
        return cameraInfo;
    }

    /**
     * 获取设备的通道数量
     *
     * @param deviceInfo 设备信息
     * @return int
     */
    private static int getChannelNum(HCNetSDK.NET_DVR_DEVICEINFO_V30 deviceInfo) {
        int maxIpChannelNum;
        if (deviceInfo.byHighDChanNum == 0) {
            maxIpChannelNum = deviceInfo.byIPChanNum & 0xff;
        } else {
            maxIpChannelNum = (int) ((deviceInfo.byHighDChanNum & 0xff) << 8);
        }
        return maxIpChannelNum;
    }

    /**
     * 获取Ip通道
     *
     * @param lUserID    用户id <p>登陆成功后返回
     * @param deviceInfo 设备信息
     * @return
     */
    private static List<CameraLogin.CameraChannel> getChannelNumber(int lUserID, int maxIpChannelNum, HCNetSDK.NET_DVR_DEVICEINFO_V30 deviceInfo) {
        List<CameraLogin.CameraChannel> cameraChannels = new ArrayList<>();
        //DVR工作状态
        HCNetSDK.NET_DVR_WORKSTATE_V30 devWork = new HCNetSDK.NET_DVR_WORKSTATE_V30();
        if (!hcNetSDK.NET_DVR_GetDVRWorkState_V30(lUserID, devWork)) {
            log.info("返回设备状态失败"); // 返回Boolean值，判断是否获取设备能力
        }
        devWork.write();

        IntByReference ibrBytesReturned = new IntByReference(0);//获取IP接入配置参数
        HCNetSDK.NET_DVR_IPPARACFG_V40 m_strIpparaCfg = new HCNetSDK.NET_DVR_IPPARACFG_V40();
        m_strIpparaCfg.write();
        //lpIpParaConfig 接收数据的缓冲指针
        Pointer lpIpParaConfig = m_strIpparaCfg.getPointer();
        boolean bRet = hcNetSDK.NET_DVR_GetDVRConfig(lUserID, HCNetSDK.NET_DVR_GET_IPPARACFG_V40, 0, lpIpParaConfig, m_strIpparaCfg.size(), ibrBytesReturned);
        m_strIpparaCfg.read();

        if (!bRet) {
            //设备不支持,则表示没有IP通道
            for (int chanNum = 0; chanNum < deviceInfo.byChanNum; chanNum++) {
                log.debug("Camera{}", (chanNum + deviceInfo.byStartChan));
            }
            return null;
        }
        //设备支持IP通道
        for (int chanNum = 0; chanNum < maxIpChannelNum; chanNum++) {
            HCNetSDK.NET_DVR_STREAM_MODE dvrStreamMode = m_strIpparaCfg.struStreamMode[chanNum];
            dvrStreamMode.read();
            if (dvrStreamMode.byGetStreamType == 0) {
                dvrStreamMode.uGetStream.setType(HCNetSDK.NET_DVR_IPCHANINFO.class);
                dvrStreamMode.uGetStream.struChanInfo.read();
                HCNetSDK.NET_DVR_IPDEVINFO_V31 dvrIpInfo = m_strIpparaCfg.struIPDevInfo[chanNum];
                int channelID = hcNetSDK.NET_DVR_SDKChannelToISAPI(lUserID, chanNum + deviceInfo.byStartDChan, true);
                int devworkChannels = (chanNum + deviceInfo.byStartDChan - 1);

                // 设置参数
                CameraLogin.CameraChannel channel = new CameraLogin.CameraChannel();
                channel.setChannelNum(channelID);
                channel.setSdkChannelNum(chanNum + deviceInfo.byStartDChan);
                channel.setIp((new String(dvrIpInfo.struIP.sIpV4)).trim());
                channel.setPort(dvrIpInfo.wDVRPort);
                channel.setUserName(new String(dvrIpInfo.sUserName).trim());
                channel.setPassword(new String(dvrIpInfo.sPassword).trim());
                channel.setOnlineState((int) m_strIpparaCfg.struStreamMode[chanNum].uGetStream.struChanInfo.byEnable);
                channel.setRecordState((int) devWork.struChanStatic[devworkChannels].byRecordStatic);
                channel.setSignalState((int) devWork.struChanStatic[devworkChannels].bySignalStatic);
                channel.setHardwareStatic((int) devWork.struChanStatic[devworkChannels].byHardwareStatic);
                cameraChannels.add(channel);
            }
        }
        return cameraChannels;
    }


    /**
     * 设置布防
     *
     * @param userId 用户id
     * @return int 返回布防id
     */
    public static int setupAlarmChan(int userId, HCNetSDK.FMSGCallBack_V31 callBack) {

        boolean b = hcNetSDK.NET_DVR_SetDVRMessageCallBack_V31(callBack, null);
        if (b) {
            log.info("设置回调成功");
        }

        //布防参数
        HCNetSDK.NET_DVR_SETUPALARM_PARAM m_strAlarmInfo = new HCNetSDK.NET_DVR_SETUPALARM_PARAM();
        m_strAlarmInfo.dwSize = m_strAlarmInfo.size();
        m_strAlarmInfo.byLevel = 1; //布防优先级：0- 一等级（高），1- 二等级（中）
        m_strAlarmInfo.byAlarmInfoType = 1; //上传报警信息类型: 0- 老报警信息(NET_DVR_PLATE_RESULT), 1- 新报警信息(NET_ITS_PLATE_RESULT)
        m_strAlarmInfo.byDeployType = 1; //布防类型：0-客户端布防，1-实时布防
        m_strAlarmInfo.write();
        int lAlarmHandle = hcNetSDK.NET_DVR_SetupAlarmChan_V41(userId, m_strAlarmInfo);
        //如果布防失败返回-1
        if (lAlarmHandle < 0) {
            log.error("布防失败！ code：{}", hcNetSDK.NET_DVR_GetLastError());
            hcNetSDK.NET_DVR_Logout(userId);  //注销
            hcNetSDK.NET_DVR_Cleanup(); //释放SDK资源
        }
        log.info("布防成功：{}", lAlarmHandle);
        return lAlarmHandle;
    }

    /**
     * 撤防
     *
     * @param alarmHandle 布防成功返回的id
     */
    public static void closeAlarmChan(Integer alarmHandle) {
        if (alarmHandle > -1) {
            if (!hcNetSDK.NET_DVR_CloseAlarmChan_V30(alarmHandle)) {
                log.error("撤防失败");
            }
        }
        log.info("撤防成功");
    }


    /**
     * 注销设备
     *
     * @param userId 用户id
     */
    public static void doLogout(int userId) {
        hcNetSDK.NET_DVR_Logout(userId);
        hcNetSDK.NET_DVR_Cleanup();
        log.info("设备：{}注销完成", userId);
    }

    /**
     * 做注销
     *
     * @param serialNumber 序列号
     */
    public static void doLogout(String serialNumber) {
        CameraLogin cameraLogin = LOCAL_CACHE.get(serialNumber);
        if (Objects.isNull(cameraLogin)) {
            return;
        }
        hcNetSDK.NET_DVR_Logout(cameraLogin.getUserId());
        hcNetSDK.NET_DVR_Cleanup();
        log.info("设备：{}注销完成", cameraLogin.getUserId());
        LOCAL_CACHE.remove(serialNumber);
    }


    /**
     * 侦听端口
     *
     * @param ip       ip
     * @param port     端口
     * @param callback 监听回调
     */
    public static void startListen(String ip, String port, HCNetSDK.FMSGCallBack_V31 callback) {
        int lListenHandle = hcNetSDK.NET_DVR_StartListen_V30(ip, Short.parseShort(port), callback, null);
        if (lListenHandle == -1) {
            log.error("监听失败" + hcNetSDK.NET_DVR_GetLastError());
            throw new RuntimeException("监听失败");
        }
        log.info("监听成功！ip:{},port:{}", ip, port);
    }

    /**
     * 停止端口监听
     */
    public static void stopListen() {
        hcNetSDK.NET_DVR_StopListen();
    }

    /**
     * <p> 下载回放视频</p>
     *
     * @param userId     登陆返回的id
     * @param channelNum 通道编号
     * @param beginTime  开始时间
     * @param endTime    结束时间
     * @param disk       磁盘目录
     */
    public static String downloadFileToDisk(int userId, int channelNum, String beginTime, String endTime, File disk) throws Exception {

        if (FileUtil.isFile(disk)) {
            throw new RuntimeException("disk参数必须是磁盘目录");
        }
        if (!FileUtil.exist(disk)) {
            FileUtil.mkdir(disk);
        }

        // 构建下载参数
        HCNetSDK.NET_DVR_PLAYCOND downloadData = new HCNetSDK.NET_DVR_PLAYCOND();
        downloadData.dwChannel = channelNum;
        downloadData.struStartTime = CommonUtil.getNvrTime(DateUtil.parseDateTime(beginTime));
        downloadData.struStopTime = CommonUtil.getNvrTime(DateUtil.parseDateTime(endTime));
        downloadData.byDrawFrame = 0;
        downloadData.byStreamType = 0;

        String videoPath = disk.getPath() + File.separator + UUID.randomUUID().toString() + ".mp4";
        int downloadRes = hcNetSDK.NET_DVR_GetFileByTime_V40(userId, videoPath, downloadData);
        if (downloadRes < 0) {
            int errorCode = hcNetSDK.NET_DVR_GetLastError();
            throw new RuntimeException("下载失败,code:" + errorCode);
        }
        log.info("下载任务句柄：{}", downloadRes);
        hcNetSDK.NET_DVR_PlayBackControl(downloadRes, HCNetSDK.NET_DVR_PLAYSTART, 0, null);
        log.info("开始下载....");

        while (true) {
            IntByReference LPOutValue = new IntByReference();
            hcNetSDK.NET_DVR_PlayBackControl(downloadRes, HCNetSDK.NET_DVR_PLAYGETPOS, 0, LPOutValue);
            int progress = LPOutValue.getValue();
            if (progress == 100) {
                log.info("文件：{},下载完成", videoPath);
                break;
            }
        }

        // 释放查询资源
        hcNetSDK.NET_DVR_StopGetFile(downloadRes);

        // 转换成mp4格式，否者在linux下无法播放
        String convertPath = disk.getPath() + File.separator + UUID.randomUUID().toString() + ".mp4";
        try {
            boolean complete = VideoConvertUtil.convert(videoPath, convertPath);
            if (complete) {
                FileUtil.del(videoPath);
                return convertPath;
            }
        } catch (Exception ignore) {
        }

        return videoPath;
    }

    /**
     * <p> 查询回放视频
     *
     * @param userId     登陆返回的id
     * @param channelNum 通道编号
     * @param beginTime  开始时间
     * @param endTime    结束时间
     */
    public static List<VideoFile> findFile(int userId, int channelNum, String beginTime, String endTime) {
        /**
         * 构建查询条件
         */
        HCNetSDK.NET_DVR_FILECOND findData = new HCNetSDK.NET_DVR_FILECOND();
        findData.dwFileType = 0;
        findData.lChannel = channelNum;
        findData.struStartTime = CommonUtil.getNvrTime(DateUtil.parseDateTime(beginTime));
        findData.struStopTime = CommonUtil.getNvrTime(DateUtil.parseDateTime(endTime));
        int v30FindRes = hcNetSDK.NET_DVR_FindFile_V30(userId, findData);
        if (v30FindRes < 0) {
            int v30ErrorCode = hcNetSDK.NET_DVR_GetLastError();
            throw new RuntimeException("文件查询失败,code:" + v30ErrorCode);
        }
        log.info("文件查询成功,句柄：{}", v30FindRes);

        HCNetSDK.NET_DVR_FINDDATA_V30 nextFind = new HCNetSDK.NET_DVR_FINDDATA_V30();
        int nextFile = 0;
        List<VideoFile> fileList = new ArrayList<>();
        while (true) {
            // 逐个获取查找到的文件信息
            nextFile = hcNetSDK.NET_DVR_FindNextFile_V30(v30FindRes, nextFind);
            // 找到文件
            if (nextFile == HCNetSDK.NET_DVR_FILE_SUCCESS) {
                String fileName = new String(nextFind.sFileName).split("\0", 2)[0];
                double fileSize = NumberUtil.div(nextFind.dwFileSize * 1.0, 1024 * 1024, 2);

                VideoFile videoFile = new VideoFile();
                videoFile.setFileName(fileName);
                videoFile.setFileSize(String.valueOf(fileSize));
                videoFile.setBeginTime(nextFind.struStartTime.toStringTime());
                videoFile.setEndTime(nextFind.struStopTime.toStringTime());
                fileList.add(videoFile);
            } else if (nextFile == HCNetSDK.NET_DVR_NOMOREFILE) {
                log.info("查找结束, 没有更多的文件");
                break;
            }
        }

        // 释放查询资源
        hcNetSDK.NET_DVR_FindClose_V30(v30FindRes);
        return fileList;
    }


    /**
     * 实时预览视频
     *
     * @param userId     登陆返回的id
     * @param channelNum 通道编号
     * @return { @link VideoPreview } 返回预览数据
     */
    public static VideoPreview startRelaPlay(int userId, int channelNum) {

        HCNetSDK.NET_DVR_PREVIEWINFO strClientInfo = new HCNetSDK.NET_DVR_PREVIEWINFO();
        strClientInfo.read();
        strClientInfo.hPlayWnd = null;  //窗口句柄，从回调取流不显示一般设置为空
        strClientInfo.lChannel = channelNum;  //通道号
        strClientInfo.dwStreamType = 0; //0-主码流，1-子码流，2-三码流，3-虚拟码流，以此类推
        strClientInfo.dwLinkMode = 0; //连接方式：0- TCP方式，1- UDP方式，2- 多播方式，3- RTP方式，4- RTP/RTSP，5- RTP/HTTP，6- HRUDP（可靠传输） ，7- RTSP/HTTPS，8- NPQ
        strClientInfo.bBlocked = 1;
        strClientInfo.write();

        try {
            PipedOutputStream outputStream = new PipedOutputStream();
            RealDataCallBack realDataCallBack = SpringContextHolder.getBean(RealDataCallBack.class);
            int playHandle = hcNetSDK.NET_DVR_RealPlay_V40(userId, strClientInfo, realDataCallBack, null);
            if (playHandle == -1) {
                int iErr = hcNetSDK.NET_DVR_GetLastError();
                log.error("取流失败,错误码：{}", iErr);
                throw new RuntimeException("取流失败");
            }
            realDataCallBack.outputStreamMap.put(String.valueOf(playHandle), outputStream);
            log.info("取流成功");
            VideoPreview videoPreview = new VideoPreview();
            videoPreview.setPlayHandler(playHandle);
            videoPreview.setUserId(userId);
            videoPreview.setType(1);
            videoPreview.setChannelNum(channelNum);
            videoPreview.setOutputStream(outputStream);
            videoPreview.setBeginTime(DateUtil.formatDateTime(new Date()));
            videoPreview.setEndTime("");

            return videoPreview;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * 停止实时播放
     *
     * @param playHandle 播放id
     */
    public static void stopRelaPlay(int playHandle) {
        log.info("停止实时预览");
        hcNetSDK.NET_DVR_StopRealPlay(playHandle);

    }

    /**
     * 播放回放视频
     *
     * @param userId     登陆返回的id
     * @param channelNum 通道编号
     * @param beginTime  开始时间
     * @param endTime    结束时间
     * @return { @link VideoPreview } 返回预览数据
     */
    public static VideoPreview startBackPlay(int userId, int channelNum, String beginTime, String endTime) {

        int playHandle = hcNetSDK.NET_DVR_PlayBackByTime(userId, channelNum, CommonUtil.getNvrTime(DateUtil.parseDateTime(beginTime)), CommonUtil.getNvrTime(DateUtil.parseDateTime(endTime)), null);

        if (playHandle < 0) {
            int v30ErrorCode = hcNetSDK.NET_DVR_GetLastError();
            throw new RuntimeException("文件查询失败,code:" + v30ErrorCode);
        }

        PipedOutputStream outputStream = new PipedOutputStream();
        BackDataCallBack backDataCallBack = SpringContextHolder.getBean(BackDataCallBack.class);
        backDataCallBack.outputStreamMap.put(StrUtil.format("{}-{}", userId, playHandle), outputStream);

        // 注册回调函数
        hcNetSDK.NET_DVR_SetPlayDataCallBack(playHandle, backDataCallBack, userId);
        // 控制录像回放状态  开始回放
        hcNetSDK.NET_DVR_PlayBackControl(playHandle, HCNetSDK.NET_DVR_PLAYSTART, 0, null);
        // 控制历史回放拉流推流时的速度和直播一致
        //hcNetSDK.NET_DVR_PlayBackControl(playHandle, HCNetSDK.NET_DVR_PLAYNORMAL,0, null);

        VideoPreview videoPreview = new VideoPreview();
        videoPreview.setPlayHandler(playHandle);
        videoPreview.setUserId(userId);
        videoPreview.setType(0);
        videoPreview.setChannelNum(channelNum);
        videoPreview.setOutputStream(outputStream);
        videoPreview.setBeginTime(beginTime);
        videoPreview.setEndTime(endTime);
        return videoPreview;
    }

    /**
     * 停止回放播放
     *
     * @param playHandle 播放id
     */
    public static void stopBackPlay(int playHandle) {
        hcNetSDK.NET_DVR_PlayBackControl(playHandle, HCNetSDK.NET_DVR_PLAYSTOPAUDIO, 0, null);
        hcNetSDK.NET_DVR_StopPlayBack(playHandle);
        log.info("停止回放预览{}", playHandle);
    }


    /**
     * 捕获图像
     *
     * @param playHandler 播放句柄
     * @return File 返回文件
     */
    public static File captureImage(int playHandler) {
        // 获取jar包所在的位置，保存抓图结果
        ApplicationHome home = new ApplicationHome(HkUtils.class);
        File source = new File(home.getSource(), "capture");

        boolean isSuccess = hcNetSDK.NET_DVR_PlayBackCaptureFile(playHandler, source.getParentFile().toString());
        if (isSuccess) {
            return source;
        }
        throw new RuntimeException("抓图失败");
    }


    /**
     * <p>rtsp 实时流推送地址</p>
     *
     * @param ip         ip
     * @param port       推流端口
     * @param userName   用户名
     * @param password   密码
     * @param channelNum 通道编号
     * @return {@link String} 返回推流结果
     */
    public static String toRtspUrl(String ip, String port, String userName, String password, int channelNum) {
        return StrUtil.format("rtsp://{}:{}@{}:{}/Streaming/Channels/{}01?transportmode=unicast", userName, password, ip, port, channelNum);
    }


    /**
     * <p>rtsp 回放流地址</p>
     *
     * @param ip         ip
     * @param port       端口
     * @param userName   用户名
     * @param password   密码
     * @param channelNum 通道编号
     * @param beginTime  开始时间 格式：yyyy-MM-dd HH:mm:ss
     * @param endTime    结束时间 格式：yyyy-MM-dd HH:mm:ss
     * @return {@link String} 返回推流结果
     */
    public static String toRtspUrl(String ip, String port, String userName, String password, int channelNum, String beginTime, String endTime) {
        return StrUtil.format("rtsp://{}:{}@{}:{}/Streaming/tracks/{}0{}?starttime={}&endtime={}", userName, password, ip, port, channelNum, 1, DateUtil.format(DateUtil.parseDateTime(beginTime), "yyyyMMdd't'HHmmss'z'"), DateUtil.format(DateUtil.parseDateTime(endTime), "yyyyMMdd't'HHmmss'z'"));
    }


    /**
     * m3u8播放
     *
     * @param rtspUrl rtsp url
     * @return {@link TaskExecutor}
     */
    public static void rtspToM3u8(String rtspUrl) {
        ThreadPoolTaskExecutor taskExecutor = SpringContextHolder.getBean("converterPoolExecutor");
        M3u8Converter converter = new M3u8Converter(rtspUrl);
        taskExecutor.submit(converter);
    }

    /**
     * rtsp flv格式
     *
     * @param rtspUrl rtsp url
     * @return {@link Thread}
     */
    public static void rtspToFlv(String rtspUrl, AsyncContext context) {
        ThreadPoolTaskExecutor taskExecutor = SpringContextHolder.getBean("converterPoolExecutor");
        FlvConverter converter = new FlvConverter(rtspUrl, context);
        taskExecutor.submit(converter);
    }

    /**
     * 视频码流转flv
     *
     * @param outputStream 输出流
     * @param context      上下文
     * @param playHandler  播放句柄
     */
    public static void streamToFlv(PipedInputStream inputStream, PipedOutputStream outputStream, AsyncContext context, Integer playHandler) {
        ThreadPoolTaskExecutor taskExecutor = SpringContextHolder.getBean("converterPoolExecutor");
        FlvConverter converter = new FlvConverter(inputStream, outputStream, context, playHandler);
        log.info("线程池主动执行任务的线程的大致数,{}", taskExecutor.getActiveCount());
        taskExecutor.submit(converter);
    }

    /**
     * 登录v40
     *
     * @param ip       IP
     * @param port     端口
     * @param userName 用户名
     * @param password 密码
     */
    public static void doLoginV40(String ip, String port, String userName, String password) {
        hcNetSDK.NET_DVR_Init();
        hcNetSDK.NET_DVR_SetConnectTime(2000, 1);
        hcNetSDK.NET_DVR_SetReconnect(10000, true);

        HCNetSDK.NET_DVR_DEVICEINFO_V40 deviceInfo = new HCNetSDK.NET_DVR_DEVICEINFO_V40();
        HCNetSDK.NET_DVR_USER_LOGIN_INFO loginInfo = new HCNetSDK.NET_DVR_USER_LOGIN_INFO();

        // 注册设备-登录参数，包括设备地址、登录用户、密码等
        loginInfo.sDeviceAddress = new byte[hcNetSDK.NET_DVR_DEV_ADDRESS_MAX_LEN];
        System.arraycopy(ip.getBytes(), 0, loginInfo.sDeviceAddress, 0, ip.length());
        loginInfo.sUserName = new byte[hcNetSDK.NET_DVR_LOGIN_USERNAME_MAX_LEN];
        System.arraycopy(userName.getBytes(), 0, loginInfo.sUserName, 0, userName.length());
        loginInfo.sPassword = new byte[hcNetSDK.NET_DVR_LOGIN_PASSWD_MAX_LEN];
        System.arraycopy(password.getBytes(), 0, loginInfo.sPassword, 0, password.length());
        loginInfo.wPort = Short.parseShort(port);
        loginInfo.bUseAsynLogin = false; //是否异步登录：0- 否，1- 是
        loginInfo.write();

        // 登录
        int lUserID = hcNetSDK.NET_DVR_Login_V40(loginInfo, deviceInfo);
        if (lUserID < 0) {
            log.error("设备{},注册失败：{}", ip, hcNetSDK.NET_DVR_GetLastError());
            throw new RuntimeException("登陆失败");
        }
        //获取当前SDK状态信息
        HCNetSDK.NET_DVR_SDKSTATE sdkState = new HCNetSDK.NET_DVR_SDKSTATE();
        boolean result = hcNetSDK.NET_DVR_GetSDKState(sdkState);

        log.info("登陆成功：{}, 设备状态：{}", lUserID, result);
    }
}
