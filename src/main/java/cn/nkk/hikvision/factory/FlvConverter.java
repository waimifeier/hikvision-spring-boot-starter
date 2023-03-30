package cn.nkk.hikvision.factory;


import cn.hutool.core.util.StrUtil;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * flv转换器
 *
 * @author dlj
 * @date 2023/03/29
 */
public class FlvConverter extends Thread implements Converter{

    private final static Logger log = LoggerFactory.getLogger(FlvConverter.class);


    private byte[] headers;

    /**
     * rtsp url
     */
    private String rtspUrl;

    /**
     * 输入流
     */
    private InputStream inputStream;

    /**
     * 异步上下文
     */
    private AsyncContext context;


    /**
     * flv转换器 通过rtsp转换
     *
     * @param rtspUrl rtsp url
     */
    public FlvConverter(String rtspUrl, AsyncContext context){
        this.rtspUrl = rtspUrl;
        this.context = context;
    }

    /**
     * flv转换器 通过输入流转换
     *
     * @param inputStream 输入流
     */
    public FlvConverter(InputStream inputStream, AsyncContext context){
        this.inputStream = inputStream;
        this.context = context;
    }

    @Override
    public void run() {
        FFmpegFrameGrabber grabber = null;
        FFmpegFrameRecorder recorder = null;
        ByteArrayOutputStream stream = null;
        try {
            grabber = Objects.nonNull(inputStream) ? new FFmpegFrameGrabber(inputStream) : new FFmpegFrameGrabber(rtspUrl);
            if (StrUtil.isNotEmpty(rtspUrl) && "rtsp".equals(rtspUrl.substring(0, 4))) {
                grabber.setOption("rtsp_transport", "tcp");
                grabber.setOption("stimeout", "5000000");
            }
            grabber.start();

            // 来源视频H264格式,音频AAC格式
            grabber.setFrameRate(25);
            if (grabber.getImageWidth() > 1920) {
                grabber.setImageWidth(1920);
            }
            if (grabber.getImageHeight() > 1080) {
                grabber.setImageHeight(1080);
            }

            stream = new ByteArrayOutputStream();
            recorder = new FFmpegFrameRecorder(stream, grabber.getImageWidth(), grabber.getImageHeight(),grabber.getAudioChannels());
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

            while (true){
                Frame f = grabber.grab();
                if (f != null) {
                    // 转码
                    recorder.record(f);
                    if (stream.size() > 0) {
                        byte[] byteArray = stream.toByteArray();
                        stream.reset();
                        try {
                            context.getResponse().getOutputStream().write(byteArray);
                        } catch (Exception e){
                            context.complete();
                            break;
                        }
                    }
                }
                TimeUnit.MILLISECONDS.sleep(5);
            }
        } catch (Exception e){
            e.printStackTrace();
        } finally {
            try {
                context.getResponse().flushBuffer();
                context.complete();
               // if(grabber != null) grabber.close();
                if(recorder != null) recorder.close();
                if(stream != null) stream.close();
            } catch (Exception e){
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
