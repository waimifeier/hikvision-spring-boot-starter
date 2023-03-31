package cn.nkk.hikvision.callBack;

import cn.nkk.hikvision.sdk.HCNetSDK;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.ByteByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;


/**
 * 实时数据回调
 */

@Component
public class RealDataCallBack implements HCNetSDK.FRealDataCallBack_V30 {

    private final static Logger log = LoggerFactory.getLogger(RealDataCallBack.class);
    public Map<Integer,PipedOutputStream> outputStreamMap=new HashMap<>();

    @Override
    public void invoke(int lRealHandle, int dwDataType, ByteByReference pBuffer, int dwBufSize, Pointer pUser) {
        if(dwBufSize > 0){
            ByteBuffer buffers = pBuffer.getPointer().getByteBuffer(0, dwBufSize);
            byte[] bytes = new byte[dwBufSize];
            buffers.rewind();
            buffers.get(bytes);
            try {
                outputStreamMap.get(lRealHandle).write(bytes);
            } catch (IOException e) {
                log.error("实时预览回调：{}",e.getMessage());
            }
        }

    }
}
