package cn.nkk.hikvision.beans;


import java.io.PipedOutputStream;

/**
 * <p>摄像头预览信息</p>
 * @author dlj
 *
 */
public class VideoPreview {

    /**
     * 用户id
     */
    private int userId;


    /**
     * 通道编号
     */
    private int channelNum;

    /**
     * 播放句柄
     */
    private int playHandler;

    /**
     * 播放类型： 0-实时预览，1-回放预览
     */
    private int type = 0;

    /**
     * 管道输出流
     * <p>需要对接管道输入流使用</p>
     */
    private PipedOutputStream outputStream;

    /**
     * 开始时间
     * <p>实时预览和回放都有开始时间</p>
     */
    private String beginTime;


    /**
     * 结束时间
     * <p>只有回放才有结束时间</p>
     */
    private String endTime;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getChannelNum() {
        return channelNum;
    }

    public void setChannelNum(int channelNum) {
        this.channelNum = channelNum;
    }

    public int getPlayHandler() {
        return playHandler;
    }

    public void setPlayHandler(int playHandler) {
        this.playHandler = playHandler;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public PipedOutputStream getOutputStream() {
        return outputStream;
    }

    public void setOutputStream(PipedOutputStream outputStream) {
        this.outputStream = outputStream;
    }

    public String getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(String beginTime) {
        this.beginTime = beginTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
}
