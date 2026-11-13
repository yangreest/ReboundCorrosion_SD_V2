package rustnet;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 复刻 MATLAB fenxi.m 中 bp_train 的单网络训练:
 * 标准化 -> He 初始化 -> Adam + Mini-batch + L2 权重衰减 -> 15% 验证集早停。
 * 网络结构 d -> 128 -> 64 -> 32 -> 1, 隐藏 ReLU, 输出线性。
 * 说明: Java 随机数生成器与 MATLAB randn 不同, 重训结果不会逐位一致, 但算法口径一致。
 */
public class BpTrainer {

    private static final double BETA1 = 0.9;
    private static final double BETA2 = 0.999;
    private static final double EPS = 1e-8;
    private static final double LR0 = 0.003;
    private static final double WD = 1e-4;
    private static final int BATCH = 64;
    private static final int MAX_EPOCH = 3000;
    private static final int PATIENCE = 200;

    /**
     * 训练单个 BP 网络。
     * @param Xtr 训练特征 [n][d]
     * @param ytr 训练标签 [n]
     * @param seed 随机种子(对应 MATLAB rng(rs*137))
     * @return 训练好的模型(保存验证集最优权重)
     */
    public static BpModel bp_train(double[][] Xtr, double[] ytr, long seed) {
        int n = Xtr.length;
        int d = Xtr[0].length;
        Random rng = new Random(seed);

        // 标准化
        double[] Xm = colMean(Xtr);
        double[] Xs = colStd(Xtr, Xm);
        double ym = mean(ytr);
        double ys = std(ytr, ym) + EPS;
        double[][] Xz = new double[n][d];
        double[] yz = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) Xz[i][j] = (Xtr[i][j] - Xm[j]) / (Xs[j] + EPS);
            yz[i] = (ytr[i] - ym) / ys;
        }

        int[] widths = {d, 128, 64, 32, 1};
        int L = widths.length;
        int nLayer = L - 1;

        List<double[][]> W = new ArrayList<>();
        List<double[][]> b = new ArrayList<>();
        for (int l = 0; l < nLayer; l++) {
            int in = widths[l], out = widths[l + 1];
            double[][] Wl = new double[out][in];
            double scale = Math.sqrt(2.0 / in);
            for (int o = 0; o < out; o++)
                for (int i = 0; i < in; i++)
                    Wl[o][i] = rng.nextGaussian() * scale;
            W.add(Wl);
            b.add(new double[out][1]);
        }
        List<double[][]> mW = zeroLike(W), vW = zeroLike(W);
        List<double[][]> mb = zeroLike(b), vb = zeroLike(b);

        // 15% 验证集
        int[] perm = permutation(n, rng);
        int nv = (int) Math.round(0.15 * n);
        int[] vi = slice(perm, 0, nv);
        int[] ti = slice(perm, nv, n);
        double[][] Xvz = rows(Xz, vi);
        double[] yvz = rows(yz, vi);

        double bestVa = Double.POSITIVE_INFINITY;
        List<double[][]> bestW = null, bestB = null;
        int bad = 0;

        for (int ep = 1; ep <= MAX_EPOCH; ep++) {
            double lr = LR0 * Math.pow(0.995, ep);
            int[] tPerm = permutation(ti.length, rng);

            for (int bs = 0; bs < ti.length; bs += BATCH) {
                int end = Math.min(bs + BATCH, ti.length);
                int nb = end - bs;
                // Xb: d × nb ; yb: 1 × nb
                double[][] Xb = new double[d][nb];
                double[][] yb = new double[1][nb];
                for (int k = 0; k < nb; k++) {
                    int row = ti[tPerm[bs + k]];
                    for (int j = 0; j < d; j++) Xb[j][k] = Xz[row][j];
                    yb[0][k] = yz[row];
                }

                // 前向
                List<double[][]> a = new ArrayList<>();
                List<double[][]> z = new ArrayList<>();
                a.add(Xb);
                for (int l = 0; l < nLayer; l++) {
                    double[][] zl = addBias(matmul(W.get(l), a.get(l)), b.get(l));
                    z.add(zl);
                    if (l < nLayer - 1) a.add(relu(zl)); else a.add(zl);
                }
                double[][] err = sub(a.get(nLayer), yb);

                // 反向
                List<double[][]> delta = new ArrayList<>();
                List<double[][]> gz = new ArrayList<>();
                for (int l = 0; l < L; l++) { delta.add(null); gz.add(null); }
                delta.set(nLayer, scale(err, 2.0 / nb));
                // 输出层(线性)
                gz.set(nLayer - 1, delta.get(nLayer));
                delta.set(nLayer - 1, matmulT(W.get(nLayer - 1), gz.get(nLayer - 1)));
                // 隐藏层 ReLU 导数
                for (int l = nLayer - 2; l >= 0; l--) {
                    gz.set(l, mulElem(delta.get(l + 1), maskPos(z.get(l))));
                    delta.set(l, matmulT(W.get(l), gz.get(l)));
                }

                // 梯度 + Adam 更新
                for (int l = 0; l < nLayer; l++) {
                    double[][] gW = add(matmul(gz.get(l), transpose(a.get(l))), scale(W.get(l), WD));
                    double[][] gb = biasMean(gz.get(l));
                    mW.set(l, lerp(mW.get(l), gW, BETA1));
                    vW.set(l, lerp(vW.get(l), mulElem(mulElem(gW, gW), 1), BETA2));
                    mb.set(l, lerp(mb.get(l), gb, BETA1));
                    vb.set(l, lerp(vb.get(l), mulElem(mulElem(gb, gb), 1), BETA2));

                    double mHat = 1 - Math.pow(BETA1, ep);
                    double vHat = 1 - Math.pow(BETA2, ep);
                    double[][] mWh = div(mW.get(l), mHat);
                    double[][] vWh = div(vW.get(l), vHat);
                    double[][] mbH = div(mb.get(l), mHat);
                    double[][] vbH = div(vb.get(l), vHat);
                    W.set(l, sub(W.get(l), scale(elemDiv(mWh, sqrtPlus(vWh, EPS)), lr)));
                    b.set(l, sub(b.get(l), scale(elemDiv(mbH, sqrtPlus(vbH, EPS)), lr)));
                }
            }

            // 验证集 MSE
            double val = valMSE(W, b, Xvz, yvz, Xm, Xs, ym, ys);
            if (val < bestVa) {
                bestVa = val;
                bestW = deepCopy(W);
                bestB = deepCopy(b);
                bad = 0;
            } else {
                bad++;
                if (bad >= PATIENCE) break;
            }
        }

        BpModel model = new BpModel();
        model.W = bestW; model.b = bestB;
        model.Xm = Xm; model.Xs = Xs; model.ym = ym; model.ys = ys;
        model.vaMSE = bestVa;
        return model;
    }

    // ---------- 矩阵辅助 ----------
    private static double[][] matmul(double[][] A, double[][] B) {
        int o = A.length, i = A[0].length, k = B[0].length;
        double[][] C = new double[o][k];
        for (int r = 0; r < o; r++)
            for (int c = 0; c < k; c++) {
                double s = 0;
                for (int t = 0; t < i; t++) s += A[r][t] * B[t][c];
                C[r][c] = s;
            }
        return C;
    }
    private static double[][] matmulT(double[][] W, double[][] g) { // Wᵀ @ g
        int in = W.length, out = W[0].length, nb = g[0].length;
        double[][] C = new double[out][nb];
        for (int r = 0; r < out; r++)
            for (int c = 0; c < nb; c++) {
                double s = 0;
                for (int t = 0; t < in; t++) s += W[t][r] * g[t][c];
                C[r][c] = s;
            }
        return C;
    }
    private static double[][] transpose(double[][] A) {
        int r = A.length, c = A[0].length;
        double[][] T = new double[c][r];
        for (int i = 0; i < r; i++) for (int j = 0; j < c; j++) T[j][i] = A[i][j];
        return T;
    }
    private static double[][] addBias(double[][] M, double[][] b) {
        double[][] R = new double[M.length][M[0].length];
        for (int i = 0; i < M.length; i++)
            for (int j = 0; j < M[0].length; j++) R[i][j] = M[i][j] + b[i][0];
        return R;
    }
    private static double[][] relu(double[][] A) {
        double[][] R = new double[A.length][A[0].length];
        for (int i = 0; i < A.length; i++) for (int j = 0; j < A[0].length; j++) R[i][j] = Math.max(0, A[i][j]);
        return R;
    }
    private static double[][] maskPos(double[][] A) {
        double[][] R = new double[A.length][A[0].length];
        for (int i = 0; i < A.length; i++) for (int j = 0; j < A[0].length; j++) R[i][j] = A[i][j] > 0 ? 1 : 0;
        return R;
    }
    private static double[][] sub(double[][] A, double[][] B) {
        double[][] R = new double[A.length][A[0].length];
        for (int i = 0; i < A.length; i++) for (int j = 0; j < A[0].length; j++) R[i][j] = A[i][j] - B[i][j];
        return R;
    }
    private static double[][] add(double[][] A, double[][] B) {
        double[][] R = new double[A.length][A[0].length];
        for (int i = 0; i < A.length; i++) for (int j = 0; j < A[0].length; j++) R[i][j] = A[i][j] + B[i][j];
        return R;
    }
    private static double[][] mulElem(double[][] A, double[][] B) {
        double[][] R = new double[A.length][A[0].length];
        for (int i = 0; i < A.length; i++) for (int j = 0; j < A[0].length; j++) R[i][j] = A[i][j] * B[i][j];
        return R;
    }
    private static double[][] mulElem(double[][] A, double s) {
        double[][] R = new double[A.length][A[0].length];
        for (int i = 0; i < A.length; i++) for (int j = 0; j < A[0].length; j++) R[i][j] = A[i][j] * s;
        return R;
    }
    private static double[][] scale(double[][] A, double s) { return mulElem(A, s); }
    private static double[][] elemDiv(double[][] A, double[][] B) {
        double[][] R = new double[A.length][A[0].length];
        for (int i = 0; i < A.length; i++) for (int j = 0; j < A[0].length; j++) R[i][j] = A[i][j] / B[i][j];
        return R;
    }
    private static double[][] div(double[][] A, double s) {
        double[][] R = new double[A.length][A[0].length];
        for (int i = 0; i < A.length; i++) for (int j = 0; j < A[0].length; j++) R[i][j] = A[i][j] / s;
        return R;
    }
    private static double[][] sqrtPlus(double[][] A, double eps) {
        double[][] R = new double[A.length][A[0].length];
        for (int i = 0; i < A.length; i++) for (int j = 0; j < A[0].length; j++) R[i][j] = Math.sqrt(A[i][j]) + eps;
        return R;
    }
    private static double[][] biasMean(double[][] G) {
        int o = G.length, nb = G[0].length;
        double[][] R = new double[o][1];
        for (int i = 0; i < o; i++) {
            double s = 0;
            for (int j = 0; j < nb; j++) s += G[i][j];
            R[i][0] = s / nb;
        }
        return R;
    }
    private static double[][] lerp(double[][] m, double[][] g, double beta) {
        double[][] R = new double[m.length][m[0].length];
        for (int i = 0; i < m.length; i++) for (int j = 0; j < m[0].length; j++) R[i][j] = beta * m[i][j] + (1 - beta) * g[i][j];
        return R;
    }
    private static List<double[][]> zeroLike(List<double[][]> src) {
        List<double[][]> out = new ArrayList<>();
        for (double[][] M : src) out.add(new double[M.length][M[0].length]);
        return out;
    }
    private static List<double[][]> deepCopy(List<double[][]> src) {
        List<double[][]> out = new ArrayList<>();
        for (double[][] M : src) {
            double[][] C = new double[M.length][M[0].length];
            for (int i = 0; i < M.length; i++) System.arraycopy(M[i], 0, C[i], 0, M[0].length);
            out.add(C);
        }
        return out;
    }
    private static double valMSE(List<double[][]> W, List<double[][]> b, double[][] Xv, double[] yv,
                                 double[] Xm, double[] Xs, double ym, double ys) {
        int n = Xv.length, d = Xm.length;
        double sse = 0;
        for (int r = 0; r < n; r++) {
            double[] a = new double[d];
            for (int j = 0; j < d; j++) a[j] = (Xv[r][j] - Xm[j]) / (Xs[j] + EPS);
            for (int l = 0; l < W.size(); l++) {
                double[][] Wl = W.get(l); double[][] bl = b.get(l);
                double[] na = new double[Wl.length];
                for (int o = 0; o < Wl.length; o++) {
                    double s = bl[o][0];
                    for (int i = 0; i < Wl[o].length; i++) s += Wl[o][i] * a[i];
                    na[o] = l < W.size() - 1 ? Math.max(0, s) : s;
                }
                a = na;
            }
            double pred = a[0] * ys + ym;
            double e = pred - yv[r];
            sse += e * e;
        }
        return sse / n;
    }

    // ---------- 统计量 ----------
    private static double mean(double[] a) { double s = 0; for (double v : a) s += v; return s / a.length; }
    private static double std(double[] a, double m) {
        double s = 0; for (double v : a) s += (v - m) * (v - m);
        return Math.sqrt(s / (a.length - 1));
    }
    private static double[] colMean(double[][] X) {
        int d = X[0].length; double[] m = new double[d];
        for (double[] row : X) for (int j = 0; j < d; j++) m[j] += row[j];
        for (int j = 0; j < d; j++) m[j] /= X.length;
        return m;
    }
    private static double[] colStd(double[][] X, double[] m) {
        int d = X[0].length; double[] s = new double[d];
        for (double[] row : X) for (int j = 0; j < d; j++) s[j] += (row[j] - m[j]) * (row[j] - m[j]);
        for (int j = 0; j < d; j++) s[j] = Math.sqrt(s[j] / (X.length - 1));
        return s;
    }
    private static int[] permutation(int n, Random rng) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = p[i]; p[i] = p[j]; p[j] = t;
        }
        return p;
    }
    private static int[] slice(int[] a, int from, int to) {
        int[] r = new int[to - from];
        System.arraycopy(a, from, r, 0, r.length);
        return r;
    }
    private static double[][] rows(double[][] X, int[] idx) {
        double[][] R = new double[idx.length][X[0].length];
        for (int i = 0; i < idx.length; i++) R[i] = X[idx[i]];
        return R;
    }
    private static double[] rows(double[] y, int[] idx) {
        double[] R = new double[idx.length];
        for (int i = 0; i < idx.length; i++) R[i] = y[idx[i]];
        return R;
    }
}
