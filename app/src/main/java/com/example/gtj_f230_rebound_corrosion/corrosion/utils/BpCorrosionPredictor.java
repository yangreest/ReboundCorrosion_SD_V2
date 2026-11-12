package com.example.gtj_f230_rebound_corrosion.corrosion.utils;

import com.blankj.utilcode.util.Utils;
import com.orhanobut.logger.Logger;

import java.io.InputStream;

import rustnet.EnsembleModel;

/**
 * BP 集成锈蚀度预测器。
 * 加载 assets/models/bp_model.txt(复制自 BP_Network/model/bp_model.txt),
 * 复用 rustnet.EnsembleModel 做前向预测, 输出百分比量纲(0~100)。
 * 模型不可用时返回 NaN, 由调用方回退原二次拟合算法。
 */
public final class BpCorrosionPredictor {

    private static volatile EnsembleModel model;
    private static volatile boolean loadFailed;

    private BpCorrosionPredictor() {
    }

    private static EnsembleModel model() {
        if (model == null && !loadFailed) {
            synchronized (BpCorrosionPredictor.class) {
                if (model == null && !loadFailed) {
                    try (InputStream in = Utils.getApp().getAssets().open("models/bp_model.txt")) {
                        model = EnsembleModel.load(in);
                    } catch (Exception e) {
                        loadFailed = true;
                        Logger.e("BP模型加载失败: " + e);
                    }
                }
            }
        }
        return model;
    }

    /**
     * 集成预测锈蚀度。
     * @param v1v7 V1~V7 特征向量(顺序与训练一致)
     * @return 预测锈蚀度百分比, 截断到 0~100; 模型不可用时返回 Double.NaN
     */
    public static double predictPercent(double[] v1v7) {
        EnsembleModel m = model();
        if (m == null) return Double.NaN;
        double y = m.bp_predict(new double[][]{v1v7})[0];
        return Math.max(0, Math.min(100, y));
    }
}