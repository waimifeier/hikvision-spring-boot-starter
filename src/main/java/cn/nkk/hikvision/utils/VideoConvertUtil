package cn.nkk.hikvision.utils;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 视频处理工具类
 */

public class VideoConvertUtil {

    private final static Logger log = LoggerFactory.getLogger(VideoConvertUtil.class);

    /**
     * 转换
     *
     * @param inputFile  输入文件
     * @param outputFile 输出文件
     * @throws Exception 异常
     */
    public static boolean convert(String inputFile, String outputFile) throws Exception {

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile);
        Frame captured_frame;
        FFmpegFrameRecorder recorder = null;

        try {
            grabber.start();

            recorder = new FFmpegFrameRecorder(outputFile, grabber.getImageWidth(), grabber.getImageHeight(), grabber.getAudioChannels());
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setFormat("mp4");
            recorder.setSampleRate(44100);
            recorder.setVideoQuality(0);
            recorder.setVideoOption("crf", "23");
            recorder.setVideoBitrate(1000000);
            recorder.setVideoOption("preset", "slow");
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            recorder.setAudioChannels(2);
            recorder.setAudioOption("crf", "0");
            recorder.setAudioQuality(0);
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            recorder.start();

            while (true) {
                captured_frame = grabber.grabFrame();
                if (captured_frame == null) {
                    log.info("转码完成");
                    break;
                }
                recorder.record(captured_frame);
            }

            return true;
        } catch (FrameRecorder.Exception e) {
            return false;
        } finally {
            if (recorder != null) {
                try {
                    recorder.close();
                } catch (Exception e) {
                    log.error("转码recorder异常：{}", e.getMessage());
                }
            }

            try {
                grabber.close();

            } catch (FrameGrabber.Exception e) {
                log.error("转码close异常异常：{}", e.getMessage());
            }
        }
    }
}
