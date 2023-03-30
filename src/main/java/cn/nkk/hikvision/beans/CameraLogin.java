package cn.nkk.hikvision.beans;


import java.util.List;

/**
 * 摄像头登陆信息
 */
public class CameraLogin {

    // 用户id
    private int userId;

    // 序列号
    private String serialNumber;

    // 通道数
    private Integer channelNum;

    // 通道列表
    private List<CameraChannel> channels;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public Integer getChannelNum() {
        return channelNum;
    }

    public void setChannelNum(Integer channelNum) {
        this.channelNum = channelNum;
    }

    public List<CameraChannel> getChannels() {
        return channels;
    }

    public void setChannels(List<CameraChannel> channels) {
        this.channels = channels;
    }

    /**
     * <p>通道信息</p>
     * @author dlj
     * @date 2023/03/18
     */
    public static class CameraChannel{

        /**
         * 通道号
         */
        private int channelNum;

        // ip
        private String ip;
        /**
         * 端口
         */
        private short port;
        /**
         * 用户名
         */
        private String userName;
        /**
         * 密码
         */
        private String password;

        /**
         * 在线状态 0-在线 1-离线
         */
        private Integer onlineState;

        // 录制状态：0-不录像，1-录像
        private Integer recordState;

        // 信号状态： 0-正常，1-信号丢失
        private Integer SignalState;

        // 硬件状态: 0-正常，1-异常
        private Integer HardwareStatic;

        public int getChannelNum() {
            return channelNum;
        }

        public void setChannelNum(int channelNum) {
            this.channelNum = channelNum;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public short getPort() {
            return port;
        }

        public void setPort(short port) {
            this.port = port;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public Integer getOnlineState() {
            return onlineState;
        }

        public void setOnlineState(Integer onlineState) {
            this.onlineState = onlineState;
        }

        public Integer getRecordState() {
            return recordState;
        }

        public void setRecordState(Integer recordState) {
            this.recordState = recordState;
        }

        public Integer getSignalState() {
            return SignalState;
        }

        public void setSignalState(Integer signalState) {
            SignalState = signalState;
        }

        public Integer getHardwareStatic() {
            return HardwareStatic;
        }

        public void setHardwareStatic(Integer hardwareStatic) {
            HardwareStatic = hardwareStatic;
        }
    }

}
