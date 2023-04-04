package cn.nkk.hikvision.factory;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.nkk.hikvision.properties.HiKProperties;
import cn.nkk.hikvision.utils.Md5Utils;
import cn.nkk.hikvision.utils.SpringContextHolder;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Objects;

import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;

/**
 * m3u8转换器
 *
 * @author dlj
 * @date 2023/03/29
 */
public class M3u8Converter extends Thread implements Converter{

    private final static Logger log = LoggerFactory.getLogger(M3u8Converter.class);

    private final String rtspUrl;

    public M3u8Converter(String rtspUrl){
        this.rtspUrl = rtspUrl;
    }


    @Override
    public void run() {
        HiKProperties hiKProperties = SpringContextHolder.getBean(HiKProperties.class);
        if(StrUtil.isEmpty(hiKProperties.getStream().getM3u8_path())){
            throw new IllegalArgumentException("请配置m3u8视频流存储路径");
        }

        String md5 = Md5Utils.encrypt16(rtspUrl);
        File file = new File(hiKProperties.getStream().getM3u8_path(), md5);
        if(!file.exists()){
            FileUtil.mkdir(file);
        }
        // 存放m3u8切片视频的目录
        String m3u8Path = FileUtil.getAbsolutePath(file);
        // 最后播放视频的时间
        long lastPlayTime = System.currentTimeMillis();
        long duration = 30 * 1000; // 30s 未获取到视频直接跳出，结束线程

        int bitrate = 2500000;// 比特率
        double framerate;// 帧率
        int timebase; // 时钟基
        FFmpegFrameGrabber grabber = null;
        FFmpegFrameRecorder recorder = null;
        try {
            avutil.av_log_set_level(avutil.AV_LOG_ERROR);
            grabber = new FFmpegFrameGrabber(rtspUrl);
            grabber.setOption("rtsp_transport", "tcp");
            grabber.setOption("allowed_media_types", "video");
            grabber.setOption("rtsp_flags", "prefer_tcp");

            grabber.setOption("stimeout", "5000000");
            grabber.start();
            framerate = 25.0;
            bitrate = grabber.getVideoBitrate();// 获取到的比特率 0

            recorder = new FFmpegFrameRecorder(m3u8Path, grabber.getImageWidth(), grabber.getImageHeight(), grabber.getAudioChannels());
            // 设置比特率
            recorder.setVideoBitrate(bitrate);
            // h264编/解码器
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            // 设置音频编码
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            // 视频帧率(保证视频质量的情况下最低25，低于25会出现闪屏)
            recorder.setFrameRate(framerate);
            // 关键帧间隔，一般与帧率相同或者是视频帧率的两倍
            recorder.setGopSize(2 * (int) framerate);
            recorder.setVideoQuality(1.0);       // 视频质量
            recorder.setVideoBitrate(10 * 1024); // 码率
            // 解码器格式
            recorder.setFormat("hls");
            // 单个切片时长,单位是s，默认为2s
            recorder.setOption("hls_time", "2");
            // HLS播放的列表长度，0 标识不做限制
            recorder.setOption("hls_list_size", "12");
            // 设置切片的ts文件序号起始值，默认从0开始，可以通过此项更改
            recorder.setOption("start_number", "0");
            //自动删除切片，如果切片数量大于hls_list_size的数量，则会开始自动删除之前的ts切片，只保留hls_list_size个数量的切片
            recorder.setOption("hls_flags", "delete_segments");
            //ts切片自动删除阈值，默认值为1，表示早于hls_list_size+1的切片将被删除
            recorder.setOption("hls_delete_threshold", "1");

            /*hls的切片类型：
             * 'mpegts'：以MPEG-2传输流格式输出ts切片文件，可以与所有HLS版本兼容。
             * 'fmp4':以Fragmented MP4(简称：fmp4)格式输出切片文件，类似于MPEG-DASH，fmp4文件可用于HLS version 7和更高版本。
             */
            recorder.setOption("hls_segment_type", "mpegts");
            AVFormatContext fc = null;
            fc = grabber.getFormatContext();
            recorder.start(fc);
            // 清空探测时留下的缓存
            AVPacket pkt = null;
            //grabber.flush();
            long dts = 0, pts = 0;// pkt的dts、pts时间戳
            long spull_start = System.currentTimeMillis();
            while ((pkt = grabber.grabPacket()) != null) {
                if (System.currentTimeMillis() - lastPlayTime > duration) {
                    //请求没有了，该结束转换了
                    break;
                }
                if (pkt.size() <= 0 || pkt.data() == null) {
                    System.out.println("no data error:");
                    continue;
                }
                if (pkt.dts() == avutil.AV_NOPTS_VALUE && pkt.pts() == avutil.AV_NOPTS_VALUE || pkt.pts() < dts) {
                    log.debug("异常pkt   当前pts: " + pkt.pts() + "  dts: " + pkt.dts() + "  上一包的pts： " + pts + " dts: "+ dts);
                    av_packet_unref(pkt);
                    continue;
                }
                // 更新上一pkt的dts，pts
                // 矫正dts，pts
                pkt.pts(pts);
                pkt.dts(dts);
                // filter.push(pkt);

                //取出过滤器合并后的图像
                //   Frame filterFrame=filter.pullImage();
                long spull_end = System.currentTimeMillis();
                if ((spull_end - spull_start) > 500)
                    log.info("pull stream take up time : {}", spull_end - spull_start);
                recorder.recordPacket(pkt);
                timebase = grabber.getFormatContext().streams(pkt.stream_index()).time_base().den();
                pts += (timebase / (int) framerate);
                dts += (timebase / (int) framerate);
                spull_start = System.currentTimeMillis();
            }

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            //可以在这里写个错误图片流返回，这样播放器就能看到问题了
        } finally {
            try {
                if(Objects.nonNull(grabber)) grabber.close();
                if(Objects.nonNull(recorder)) recorder.close();
                FileUtil.del(m3u8Path);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public String getKey() {
        return null;
    }
}
