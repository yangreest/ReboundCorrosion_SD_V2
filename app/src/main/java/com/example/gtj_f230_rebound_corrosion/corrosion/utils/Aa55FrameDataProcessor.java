package com.example.gtj_f230_rebound_corrosion.corrosion.utils;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 新协议（AA 55 帧头）蓝牙数据解析器。
 * <p>
 * 上传测量数据帧格式（整帧 11 字节）：
 * AA 55 | CMD(0x87) | LEN(0x05) | 信号强度(3字节) | 小车位置(2字节) | CRC16_H | CRC16_L
 * <p>
 * 采用与 {@link OptimizedDataProcessor} 相同的生产者-消费者线程模型，
 * 回调接口保持一致，可直接替换使用。
 */
public class Aa55FrameDataProcessor {

    //帧头
    private static final byte FRAME_HEAD_1 = (byte) 0xAA;
    private static final byte FRAME_HEAD_2 = (byte) 0x55;
    //上传数据命令码
    private static final byte CMD_UPLOAD = (byte) 0x87;
    //数据域长度
    private static final byte DATA_LEN = (byte) 0x05;
    //整帧长度：2(帧头)+1(CMD)+1(LEN)+5(DATA)+2(CRC)=11
    private static final int FRAME_LENGTH = 11;

    //锁
    private final Object lock = new Object();
    //线程安全的阻塞队列
    private final LinkedBlockingQueue<byte[]> dataQueue = new LinkedBlockingQueue<>(2000);
    //处理线程的状态
    private volatile boolean isProcessing;
    //正在被拼接处理的数据
    private byte[] currentByte;
    //数据回调
    private final DataProcessor dataProcessor;

    public interface DataProcessor {
        //子线程回调：position 小车位置、signal 信号强度、frameData 完整帧（十六进制字符串）
        void processData(int position, int signal, String frameData);
    }

    public Aa55FrameDataProcessor(DataProcessor dataProcessor) {
        this.dataProcessor = dataProcessor;
    }

    /**
     * 接收蓝牙帧数据（非阻塞入队），并确保只有一个处理线程在运行。
     *
     * @param data：接收到的蓝牙原始数据
     */
    public void receiveDataOptimized(byte[] data) {
        boolean offered = dataQueue.offer(data);
        if (!offered) {
            return;
        }
        synchronized (lock) {
            if (!isProcessing) {
                isProcessing = true;
                startProcessingThread();
            }
        }
    }

    /**
     * 开启线程处理数据。
     */
    private void startProcessingThread() {
        new Thread(() -> {
            try {
                while (true) {
                    byte[] data = dataQueue.poll(10, TimeUnit.MILLISECONDS);
                    if (data == null) {
                        synchronized (lock) {
                            if (dataQueue.isEmpty()) {
                                isProcessing = false;
                                break;
                            }
                        }
                    } else {
                        treatData(data);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * 拼接并循环解帧。
     *
     * @param data：待拼接的数据
     */
    private void treatData(byte[] data) {
        currentByte = ByteArrayUtils.concat(currentByte, data);
        while (true) {
            //查找帧头 AA 55
            int headIndex = findHeader(currentByte);
            if (headIndex < 0) {
                //无帧头，仅保留最后 1 字节（可能是被截断的 0xAA）
                if (currentByte.length > 1) {
                    currentByte = ByteArrayUtils.subarrayFrom(currentByte, currentByte.length - 1);
                }
                break;
            }
            //丢弃帧头之前的无效字节
            if (headIndex > 0) {
                currentByte = ByteArrayUtils.subarrayFrom(currentByte, headIndex);
            }
            //数据不足一整帧，等待后续数据
            if (currentByte.length < FRAME_LENGTH) {
                break;
            }
            //校验 CMD 与 LEN，非法则跳过当前帧头字节重新查找
            if (currentByte[2] != CMD_UPLOAD || currentByte[3] != DATA_LEN) {
                currentByte = ByteArrayUtils.subarrayFrom(currentByte, 1);
                continue;
            }
            //取出一整帧并解析
            byte[] frame = ByteArrayUtils.subarrayLength(currentByte, 0, FRAME_LENGTH);
            currentByte = ByteArrayUtils.subarrayFrom(currentByte, FRAME_LENGTH);
            parseFrame(frame);
        }
    }

    /**
     * 查找帧头 AA 55 的位置。
     */
    private int findHeader(byte[] array) {
        if (array == null) {
            return -1;
        }
        for (int i = 0; i < array.length - 1; i++) {
            if (array[i] == FRAME_HEAD_1 && array[i + 1] == FRAME_HEAD_2) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 解析单帧数据。
     * <p>
     * 数据域 5 字节：前 3 字节为信号强度，后 2 字节为小车位置，均按大端（高位在前）解析。
     *
     * @param frame：完整的一帧（11 字节）
     */
    private void parseFrame(byte[] frame) {
        //信号强度（3字节，frame[4..6]）
        int signal = ((frame[4] & 0xFF) << 16) | ((frame[5] & 0xFF) << 8) | (frame[6] & 0xFF);
        //小车位置（2字节，frame[7..8]）
        int position = ((frame[7] & 0xFF) << 8) | (frame[8] & 0xFF);
        dataProcessor.processData(position, signal, ByteArrayUtils.toHexString(frame));
    }
}