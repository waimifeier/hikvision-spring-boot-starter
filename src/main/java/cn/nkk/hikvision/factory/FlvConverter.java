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
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

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
     * @param outputStream 输出流
     * @param context 上下文
     * @param playHandler 播放句柄
     */
    public FlvConverter(PipedOutputStream outputStream,AsyncContext context,Integer playHandler) {
        this.context = context;
        this.outputStream = outputStream;
        this.playHandler = playHandler;
    }

    @Override
    public void run() {
        FFmpegFrameGrabber grabber = null;
        FFmpegFrameRecorder recorder = null;
        ByteArrayOutputStream stream = null;
        PipedInputStream inputStream = null;
        try {

            if(Objects.nonNull(outputStream)){
                inputStream = new PipedInputStream(this.outputStream);
                grabber = new FFmpegFrameGrabber(inputStream,0);
            }else {
                grabber = new FFmpegFrameGrabber(rtspUrl);
            }
            if (StrUtil.isNotEmpty(rtspUrl) && rtspUrl.startsWith("rtsp")) {
                grabber.setOption("rtsp_transport", "tcp");
                grabber.setOption("stimeout", "5000000");
                log.info("rtsp链接------------------------");
            }
            grabber.start();
            int videoCodec = grabber.getVideoCodec();
            log.info("启动grabber,编码{}------------------------",videoCodec);
            if (grabber.getImageWidth() > 1920) {
                grabber.setImageWidth(1920);
            }
            if (grabber.getImageHeight() > 1080) {
                grabber.setImageHeight(1080);
            }

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
                log.info("执行资源回收----------------------");
                if(this.outputStream!=null) this.outputStream.close();
                if(inputStream != null) inputStream.close();
                if(this.playHandler!= null) HkUtils.stopBackPlay(this.playHandler);
                log.info("执行资源回收2----------------------");
                if(grabber!= null) grabber.close();
                if (recorder != null) recorder.close();
                if (stream != null) stream.close();
                log.info("执行资源回收3----------------------");
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
