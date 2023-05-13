package cn.nkk.hikvision.factory;


import cn.hutool.core.util.StrUtil;
import cn.nkk.hikvision.utils.HkUtils;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * flv转换器
 *
 * @author dlj
 * @date 2023/03/29
 */
/**
 * flv转换器
 *
 * @author dlj
 * @date 2023/03/29
 */
public class FlvConverter extends Thread implements Converter{

    private static final Logger log = LoggerFactory.getLogger(FlvConverter.class);
    private byte[] headers;
    private String rtspUrl;
    private PipedInputStream inputStream;
    private AsyncContext context;


    private Integer playHandler;

    private PipedOutputStream outputStream;

    public FlvConverter(String rtspUrl, AsyncContext context) {
        this.rtspUrl = rtspUrl;
        this.context = context;
    }

    /**
     * flv转换器 通过输入流转换
     *
     * @param inputStream 输入流
     */
    public FlvConverter(PipedInputStream inputStream, PipedOutputStream outputStream, AsyncContext context, Integer playHandler) {
        this.inputStream = inputStream;
        this.context = context;
        this.outputStream = outputStream;
        this.playHandler = playHandler;
    }

    @Override
    public void run() {
        FFmpegFrameGrabber grabber = null;
        FFmpegFrameRecorder recorder = null;
        ByteArrayOutputStream stream = null;
        try {
            log.info("进入grabber------------------------");
            grabber = Objects.nonNull(inputStream) ? new FFmpegFrameGrabber(inputStream,0) : new FFmpegFrameGrabber(rtspUrl);
            if (StrUtil.isNotEmpty(rtspUrl) && rtspUrl.startsWith("rtsp")) {
                grabber.setOption("rtsp_transport", "tcp");
                //首选TCP进行RTP传输
                grabber.setOption("rtsp_flags", "prefer_tcp");
                log.info("rtsp链接------------------------");
            }
            if(Objects.nonNull(inputStream)){
                //检测管道流中是否存在数据，如果2s后依然没有写入1024的数据，则认为管道流中无数据，避免grabber.start();发生阻塞
                long stime = new Date().getTime();
                while (true) {
                    Thread.sleep(100);
                    if (new Date().getTime() - stime > 2000) {
                        return;
                    }
                    if (inputStream.available() == 1024) {
                        break;
                    }
                }
            }
            // 设置缓存大小，提高画质、减少卡顿花屏
            grabber.setOption("buffer_size", "1024000");
            grabber.startUnsafe();
            int videoCodec = grabber.getVideoCodec();
            log.info("启动grabber,编码{}------------------------",videoCodec);
            grabber.setImageWidth(640);
            grabber.setImageHeight(480);

            stream = new ByteArrayOutputStream();
            recorder = new FFmpegFrameRecorder(stream, grabber.getImageWidth(), grabber.getImageHeight(), grabber.getAudioChannels());
            recorder.setInterleaved(true);
            recorder.setVideoOption("preset", "ultrafast");
            recorder.setVideoOption("tune", "zerolatency");
            recorder.setVideoOption("crf", "25");
            recorder.setGopSize(50);
            recorder.setFrameRate(25);
            recorder.setSampleRate(grabber.getSampleRate());
            if (grabber.getAudioChannels() > 0) {
                recorder.setAudioChannels(grabber.getAudioChannels());
                recorder.setAudioBitrate(grabber.getAudioBitrate());
                recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            }
            recorder.setFormat("flv");
            recorder.setVideoBitrate(grabber.getVideoBitrate());
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.start();
            if (headers == null) {
                headers = stream.toByteArray();
                stream.reset();
                writeResponse(headers);
            }

            while (true) {
                Frame f = grabber.grab();
                if (f != null) {
                    // 转码
                    recorder.record(f);
                    if (stream.size() > 0) {
                        byte[] byteArray = stream.toByteArray();
                        stream.reset();
                        try {
                            context.getResponse().getOutputStream().write(byteArray);
                        } catch (Exception e) {
                            context.complete();
                            break;
                        }
                    }
                }
                TimeUnit.MILLISECONDS.sleep(5);
            }
        } catch (Exception e) {
            log.info("异步出错------------------------"+e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if(this.outputStream!=null) this.outputStream.close();
                if(this.inputStream != null) this.inputStream.close();
                if(this.playHandler!= null) HkUtils.stopBackPlay(this.playHandler);
                if(grabber!= null) grabber.close();
                if (recorder != null) recorder.close();
                if (stream != null) stream.close();
                log.info("资源回收完成----------------------");
                context.getResponse().flushBuffer();
                context.complete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }


    /**
     * 依次写出队列中的上下文
     *
     * @param bytes bytes
     */
    public void writeResponse(byte[] bytes) {
        try {
            context.getResponse().getOutputStream().write(bytes);
        } catch (Exception e) {
            context.complete();
        }
    }



    @Override
    public String getKey() {
        return null;
    }
}
