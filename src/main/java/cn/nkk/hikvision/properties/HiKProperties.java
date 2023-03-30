package cn.nkk.hikvision.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 海康sdk配置类
 */
@ConfigurationProperties(prefix = "hik")
public class HiKProperties {

    /**
     * 海康sdk位置
     */
    private String sdk_path;


    /**
     * 拉流配置
     */
    public PullStream stream;

    /**
     * 线程池配置
     */
    private HikPool pool;


    public static class HikPool{
        /**
         * 核心线程数
         */
        private int core_pool_size = 10;

        /**
         * 最大线程数
         */
        private int max_pool_size = 200;

        /**
         * 队列大小
         */
        private int queue_capacity = 1024;

        /**
         * 空闲线程活跃时间（秒)
         */
        private int keep_alive_seconds = 60;


        public int getCore_pool_size() {
            return core_pool_size;
        }

        public void setCore_pool_size(int core_pool_size) {
            this.core_pool_size = core_pool_size;
        }

        public int getMax_pool_size() {
            return max_pool_size;
        }

        public void setMax_pool_size(int max_pool_size) {
            this.max_pool_size = max_pool_size;
        }

        public int getQueue_capacity() {
            return queue_capacity;
        }

        public void setQueue_capacity(int queue_capacity) {
            this.queue_capacity = queue_capacity;
        }

        public int getKeep_alive_seconds() {
            return keep_alive_seconds;
        }

        public void setKeep_alive_seconds(int keep_alive_seconds) {
            this.keep_alive_seconds = keep_alive_seconds;
        }

        @Override
        public String toString() {
            return "HikPool{" +
                    "core_pool_size=" + core_pool_size +
                    ", max_pool_size=" + max_pool_size +
                    ", queue_capacity=" + queue_capacity +
                    ", keep_alive_seconds=" + keep_alive_seconds +
                    '}';
        }
    }


    public static class PullStream{

        /**
         * m3u8磁盘路径
         */
        private String m3u8_path;

        public String getM3u8_path() {
            return m3u8_path;
        }

        public void setM3u8_path(String m3u8_path) {
            this.m3u8_path = m3u8_path;
        }

        @Override
        public String toString() {
            return "PullStream{" +
                    "m3u8_path='" + m3u8_path + '\'' +
                    '}';
        }
    }


    public String getSdk_path() {
        return sdk_path;
    }

    public void setSdk_path(String sdk_path) {
        this.sdk_path = sdk_path;
    }

    public PullStream getStream() {
        return stream;
    }

    public void setStream(PullStream stream) {
        this.stream = stream;
    }

    public HikPool getPool() {
        return pool;
    }

    public void setPool(HikPool pool) {
        this.pool = pool;
    }

    @Override
    public String toString() {
        return "HiKProperties{" +
                "sdk_path='" + sdk_path + '\'' +
                ", stream=" + stream +
                ", pool=" + pool +
                '}';
    }
}
