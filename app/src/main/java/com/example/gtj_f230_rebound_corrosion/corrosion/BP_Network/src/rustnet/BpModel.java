package rustnet;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个 BP 前馈神经网络模型。
 * 对应 MATLAB fenxi.m 中的 bp_train 训练结果与 bp_predict 预测函数。
 * 结构: 7 -> 128 -> 64 -> 32 -> 1, 隐藏层 ReLU, 输出层线性。
 * 输入/目标均按训练集均值方差标准化, 预测时反归一化还原到原始量纲。
 */
public class BpModel {

    /** 各层权重 W[l]: [out][in] */
    public List<double[][]> W = new ArrayList<>();
    /** 各层偏置 b[l]: [out][1] */
    public List<double[][]> b = new ArrayList<>();
    /** 输入标准化均值 (1×d) */
    public double[] Xm;
    /** 输入标准化标准差 (1×d) */
    public double[] Xs;
    /** 目标标准化均值 / 标准差 */
    public double ym, ys;
    /** 训练时验证集 MSE (仅供参考) */
    public double vaMSE = Double.POSITIVE_INFINITY;

    /**
     * 单样本前向预测 (对应 MATLAB bp_predict)。
     * @param x 原始特征向量 (V1~V7, 顺序与训练一致)
     * @return 预测锈蚀度(原始量纲)
     */
    public double bp_predict(double[] x) {
        int d = Xm.length;
        // 输入标准化
        double[] a = new double[d];
        for (int i = 0; i < d; i++) {
            a[i] = (x[i] - Xm[i]) / (Xs[i] + 1e-8);
        }
        int nLayers = W.size();
        // 隐藏层 ReLU, 最后一层线性
        for (int l = 0; l < nLayers; l++) {
            double[][] Wl = W.get(l);
            double[][] bl = b.get(l);
            int out = Wl.length;
            double[] na = new double[out];
            for (int o = 0; o < out; o++) {
                double s = bl[o][0];
                for (int i = 0; i < Wl[o].length; i++) {
                    s += Wl[o][i] * a[i];
                }
                if (l < nLayers - 1) {
                    na[o] = Math.max(0, s);   // ReLU
                } else {
                    na[o] = s;               // 输出层线性
                }
            }
            a = na;
        }
        // 反归一化
        return a[0] * ys + ym;
    }

    /**
     * 批量预测 (对应 MATLAB bp_predict)。
     * @param X 样本矩阵 [n][d]
     * @return 预测锈蚀度数组 [n]
     */
    public double[] bp_predict(double[][] X) {
        double[] yp = new double[X.length];
        for (int i = 0; i < X.length; i++) {
            yp[i] = bp_predict(X[i]);
        }
        return yp;
    }
}
