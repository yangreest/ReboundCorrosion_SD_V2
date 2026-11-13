package rustnet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 训练入口(对应 MATLAB fenxi.m)。
 * 读取训练 CSV, nRestart 次重启训练单网络, 按验证 MSE 取 Top-kTop 集成(bestModels),
 * 保存为 EnsembleModel 可加载的文本模型, 并打印训练集回代指标。
 *
 * 用法:
 *   java rustnet.fenxi <训练数据.csv> [输出模型.txt]
 */
public class fenxi {

    public static void main(String[] args) throws IOException {
        String dataPath = args.length > 0 ? args[0] : "data.csv";
        String outPath = args.length > 1 ? args[1] : "model/bp_model.txt";

        // ---- 与 fenxi.m 一致的变量名 ----
        int nRestart = 10;
        int kTop = 5;
        double ACC_THRESH = 5.0;
        String[] featureNames = {"V1", "V2", "V3", "V4", "V5", "V6", "V7"};

        List<String[]> rows = readCsv(dataPath);
        String[] colNames = rows.get(0);
        int[] idxV = new int[7];
        for (int k = 0; k < 7; k++) idxV[k] = findExact(colNames, "V" + (k + 1));
        int idx_y = findExact(colNames, "锈蚀度");
        int idx_ratio = findContains(colNames, new String[]{"商值"});
        int idx_rust = findContains(colNames, new String[]{"锈蚀量", "腐蚀量", "深度"});
        if (idx_y < 0) throw new IOException("找不到标签列 锈蚀度");

        int n_raw = rows.size() - 1;
        double[][] V = new double[n_raw][7];
        double[] y_true = new double[n_raw];
        double[] x_ratio = new double[n_raw];
        double[] rust_amt = new double[n_raw];
        for (int r = 0; r < n_raw; r++) {
            String[] c = rows.get(r + 1);
            for (int k = 0; k < 7; k++) V[r][k] = parse(c, idxV[k]);
            y_true[r] = parse(c, idx_y);
            x_ratio[r] = idx_ratio >= 0 ? parse(c, idx_ratio) : Double.NaN;
            rust_amt[r] = idx_rust >= 0 ? parse(c, idx_rust) : Double.NaN;
        }

        // 清洗: 去重 -> 去缺失 -> 异常过滤(与 fenxi.m 一致)
        Set<String> seen = new HashSet<>();
        List<double[]> gXl = new ArrayList<>();
        List<Double> gyl = new ArrayList<>();
        for (int r = 0; r < n_raw; r++) {
            StringBuilder key = new StringBuilder();
            for (int k = 0; k < 7; k++) key.append(V[r][k]).append(',');
            if (idx_ratio >= 0) key.append(x_ratio[r]);
            if (!seen.add(key.toString())) continue;
            boolean nan = false;
            for (int k = 0; k < 7; k++) if (Double.isNaN(V[r][k])) nan = true;
            if (Double.isNaN(y_true[r]) || (idx_ratio >= 0 && Double.isNaN(x_ratio[r]))
                    || (idx_rust >= 0 && Double.isNaN(rust_amt[r]))) nan = true;
            if (nan) continue;
            boolean ok = y_true[r] > 0 && rust_amt[r] >= 0;
            if (idx_ratio >= 0) ok = ok && x_ratio[r] > 0.5 && x_ratio[r] < 1.1;
            if (!ok) continue;
            gXl.add(V[r]); gyl.add(y_true[r]);
        }
        int Ng = gXl.size();
        double[][] gX = gXl.toArray(new double[0][]);
        double[] gy = new double[Ng];
        for (int i = 0; i < Ng; i++) gy[i] = gyl.get(i);
        System.out.printf("==> 训练样本: %d%n", Ng);

        // nRestart 次重启, 取 Top-kTop
        List<BpModel> all = new ArrayList<>();
        List<Double> bestVa = new ArrayList<>();
        for (int rs = 1; rs <= nRestart; rs++) {
            BpModel m = BpTrainer.bp_train(gX, gy, rs * 137L);
            all.add(m);
            bestVa.add(m.vaMSE);
            System.out.printf("  模型%2d/%d  验证MSE=%.5f%n", rs, nRestart, m.vaMSE);
        }
        all.sort(Comparator.comparingDouble(a -> a.vaMSE));
        List<BpModel> bestModels = new ArrayList<>(all.subList(0, Math.min(kTop, all.size())));

        // 集成回代预测 ypn
        double[] ypn = new double[Ng];
        for (BpModel m : bestModels) {
            double[] p = m.bp_predict(gX);
            for (int i = 0; i < Ng; i++) ypn[i] += p[i];
        }
        for (int i = 0; i < Ng; i++) ypn[i] /= bestModels.size();

        double R2n = r2(gy, ypn);
        double RMSEN = rmse(gy, ypn);
        boolean[] ibn = new boolean[Ng];
        int accn = 0;
        for (int i = 0; i < Ng; i++) {
            ibn[i] = Math.abs(gy[i] - ypn[i]) <= ACC_THRESH;
            if (ibn[i]) accn++;
        }
        System.out.printf("%n  BP集成: R2=%.4f  RMSE=%.3f  准确率=%.1f%% (%d/%d)%n",
                R2n, RMSEN, 100.0 * accn / Ng, accn, Ng);

        saveEnsemble(bestModels, kTop, ACC_THRESH, featureNames, Paths.get(outPath));
        System.out.println("==> 集成模型已保存: " + outPath);
    }

    /** 保存为 EnsembleModel 可读取的文本格式(对应 MATLAB save bp_ensemble_model.mat)。 */
    public static void saveEnsemble(List<BpModel> bestModels, int kTop, double ACC_THRESH,
                                    String[] featureNames, Path out) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# BP ensemble model, saved by Java fenxi\n");
        sb.append("kTop ").append(kTop).append('\n');
        sb.append("accThresh ").append(ACC_THRESH).append('\n');
        sb.append("nModels ").append(bestModels.size()).append('\n');
        sb.append("feature ").append(String.join(" ", featureNames)).append('\n');

        for (int mi = 0; mi < bestModels.size(); mi++) {
            BpModel m = bestModels.get(mi);
            sb.append("=== model ").append(mi).append(" ===\n");
            sb.append("Xm ").append(join(m.Xm)).append('\n');
            sb.append("Xs ").append(join(m.Xs)).append('\n');
            sb.append("ym ").append(fmt(m.ym)).append('\n');
            sb.append("ys ").append(fmt(m.ys)).append('\n');
            sb.append("nLayers ").append(m.W.size()).append('\n');
            for (int l = 0; l < m.W.size(); l++) {
                double[][] Wl = m.W.get(l); double[][] bl = m.b.get(l);
                sb.append("W ").append(l).append(' ').append(Wl.length).append(' ').append(Wl[0].length).append('\n');
                for (double[] row : Wl) sb.append(join(row)).append('\n');
                sb.append("b ").append(l).append(' ').append(bl.length).append('\n');
                double[] bv = new double[bl.length];
                for (int o = 0; o < bl.length; o++) bv[o] = bl[o][0];
                sb.append(join(bv)).append('\n');
            }
        }
        if (out.toAbsolutePath().getParent() != null)
            Files.createDirectories(out.toAbsolutePath().getParent());
        Files.write(out, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String join(double[] v) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < v.length; i++) { if (i > 0) s.append(' '); s.append(fmt(v[i])); }
        return s.toString();
    }
    private static String fmt(double v) { return String.format("%.10g", v); }

    private static int findExact(String[] head, String name) {
        for (int i = 0; i < head.length; i++) if (head[i].trim().equals(name)) return i;
        return -1;
    }
    private static int findContains(String[] head, String[] kw) {
        for (int i = 0; i < head.length; i++) {
            String c = head[i].trim();
            for (String k : kw) if (c.contains(k)) return i;
        }
        return -1;
    }
    private static double parse(String[] c, int idx) {
        if (idx < 0 || idx >= c.length) return Double.NaN;
        String s = c[idx].trim().replaceAll("^\"|\"$", "");
        if (s.isEmpty()) return Double.NaN;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return Double.NaN; }
    }
    private static List<String[]> readCsv(String path) throws IOException {
        List<String[]> out = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty()) continue;
            List<String> f = parseCsvLine(line);
            f.set(0, f.get(0).replace("\ufeff", ""));
            out.add(f.toArray(new String[0]));
        }
        return out;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQ) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else inQ = false;
                } else cur.append(ch);
            } else {
                if (ch == '"') inQ = true;
                else if (ch == ',') { out.add(cur.toString()); cur.setLength(0); }
                else cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }
    private static double r2(double[] y, double[] yp) {
        double ym = 0; for (double v : y) ym += v; ym /= y.length;
        double sr = 0, st = 0;
        for (int i = 0; i < y.length; i++) { sr += (y[i]-yp[i])*(y[i]-yp[i]); st += (y[i]-ym)*(y[i]-ym); }
        return st == 0 ? Double.NaN : 1 - sr/st;
    }
    private static double rmse(double[] y, double[] yp) {
        double s = 0; for (int i = 0; i < y.length; i++) s += (y[i]-yp[i])*(y[i]-yp[i]);
        return Math.sqrt(s/y.length);
    }
}
