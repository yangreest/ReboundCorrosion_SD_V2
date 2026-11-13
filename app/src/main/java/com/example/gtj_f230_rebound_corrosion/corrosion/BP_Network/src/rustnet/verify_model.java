package rustnet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 外部数据验证主程序(对应 MATLAB verify_model.m)。
 * 加载集成模型 bestModels, 读取 CSV 数据(由 Excel 另存为 CSV), 复用与训练一致的
 * 列识别 / 去重 / 去缺失 / 异常过滤规则, 输出预测锈蚀度 ypn; 若含真实标签列 y_true,
 * 则计算 R2n / RMSEN / 准确率 accn(绝对误差 <= ACC_THRESH), 并导出结果 CSV。
 *
 * 用法:
 *   java rustnet.verify_model <数据.csv> [模型 bp_model.txt]
 * 不传参数时默认读取当前目录 data.csv 与 model/bp_model.txt。
 */
public class verify_model {

    public static void main(String[] args) throws IOException {
        String modelFile = args.length > 1 ? args[1] : "model/bp_model.txt";
        String filepath = args.length > 0 ? args[0] : "data.csv";

        // ---- 加载已训练模型(对应 verify_model.m 第 1 节) ----
        EnsembleModel S = EnsembleModel.load(Paths.get(modelFile));
        List<BpModel> bestModels = S.bestModels;
        int kTop = S.kTop;
        double ACC_THRESH = S.ACC_THRESH;
        String[] featureNames = S.featureNames;
        System.out.printf("==> 已加载模型: %s (Top-%d)%n", modelFile, kTop);
        System.out.println("==> 输入特征顺序: " + String.join(", ", featureNames));

        // ---- 读取与列识别 ----
        List<String[]> rows = readCsv(filepath);
        if (rows.isEmpty()) throw new IOException("CSV 无数据: " + filepath);
        String[] colNames = rows.get(0);
        System.out.printf("==> 原始尺寸: %d 行 x %d 列%n", rows.size() - 1, colNames.length);

        int idx_V1 = findExact(colNames, "V1");
        int idx_V2 = findExact(colNames, "V2");
        int idx_V3 = findExact(colNames, "V3");
        int idx_V4 = findExact(colNames, "V4");
        int idx_V5 = findExact(colNames, "V5");
        int idx_V6 = findExact(colNames, "V6");
        int idx_V7 = findExact(colNames, "V7");
        if (idx_V1 < 0 || idx_V2 < 0 || idx_V3 < 0 || idx_V4 < 0 || idx_V5 < 0 || idx_V6 < 0 || idx_V7 < 0)
            throw new IOException("找不到完整的 V1~V7 列");

        int idx_y = findContains(colNames, new String[]{"锈蚀度", "腐蚀度"}, new String[]{"测试", "软件"});
        int idx_ratio = findContains(colNames, new String[]{"商值"}, new String[]{});
        int idx_rust = findContains(colNames, new String[]{"腐蚀量", "锈蚀量", "深度"}, new String[]{});
        int idx_cov = findExact(colNames, "保护层厚度");
        boolean hasTrue = idx_y >= 0;
        if (!hasTrue) System.out.println("  提示: 文件中未找到标签列, 仅做预测, 不计算精度指标。");

        int n = rows.size() - 1;
        double[][] V = new double[n][7];
        double[] y_true = new double[n];
        double[] x_ratio = new double[n];
        double[] rust_amt = new double[n];
        double[] cov_thk = new double[n];
        List<Double> yForScale = new ArrayList<>();

        for (int r = 0; r < n; r++) {
            String[] c = rows.get(r + 1);
            V[r][0] = parse(c, idx_V1); V[r][1] = parse(c, idx_V2); V[r][2] = parse(c, idx_V3);
            V[r][3] = parse(c, idx_V4); V[r][4] = parse(c, idx_V5); V[r][5] = parse(c, idx_V6);
            V[r][6] = parse(c, idx_V7);
            y_true[r] = hasTrue ? parse(c, idx_y) : Double.NaN;
            x_ratio[r] = idx_ratio >= 0 ? parse(c, idx_ratio) : Double.NaN;
            rust_amt[r] = idx_rust >= 0 ? parse(c, idx_rust) : Double.NaN;
            cov_thk[r] = idx_cov >= 0 ? parse(c, idx_cov) : Double.NaN;
            if (hasTrue && !Double.isNaN(y_true[r])) yForScale.add(y_true[r]);
        }

        // 标签量纲自动判断: 中位数 <2 视为比例, ×100 换算到训练量纲
        double LABEL_SCALE = 1.0;
        if (hasTrue && !yForScale.isEmpty()) {
            if (median(yForScale) < 2) LABEL_SCALE = 100.0;
        }

        // ---- 数据清洗(与训练一致) ----
        Set<String> seen = new HashSet<>();
        List<double[]> gXl = new ArrayList<>();
        List<Double> ytl = new ArrayList<>();
        int nDup = 0, nNan = 0, nBad = 0;

        for (int r = 0; r < n; r++) {
            StringBuilder key = new StringBuilder();
            for (int k = 0; k < 7; k++) key.append(V[r][k]).append(',');
            if (idx_ratio >= 0) key.append(x_ratio[r]);
            if (!seen.add(key.toString())) { nDup++; continue; }

            boolean nan = false;
            for (int k = 0; k < 7; k++) if (Double.isNaN(V[r][k])) nan = true;
            if (hasTrue && Double.isNaN(y_true[r])) nan = true;
            if (idx_ratio >= 0 && Double.isNaN(x_ratio[r])) nan = true;
            if (idx_rust >= 0 && Double.isNaN(rust_amt[r])) nan = true;
            if (idx_cov >= 0 && Double.isNaN(cov_thk[r])) nan = true;
            if (nan) { nNan++; continue; }

            boolean ok = true;
            if (idx_ratio >= 0) ok = ok && x_ratio[r] > 0.5 && x_ratio[r] < 1.1;
            if (hasTrue) ok = ok && y_true[r] > 0;
            if (idx_rust >= 0) ok = ok && rust_amt[r] >= 0;
            if (!ok) { nBad++; continue; }

            gXl.add(V[r]);
            ytl.add(hasTrue ? y_true[r] * LABEL_SCALE : Double.NaN);
        }

        int Ng = gXl.size();
        System.out.printf("  重复行: %d 行已移除%n", nDup);
        System.out.printf("  含缺失值行: %d 行已移除%n", nNan);
        System.out.printf("  异常值行: %d 行已移除%n", nBad);
        System.out.printf("  ==> 清洗后有效样本: %d%n", Ng);
        if (Ng == 0) throw new IOException("清洗后无有效样本, 请检查数据列与取值范围。");

        double[][] gX = gXl.toArray(new double[0][]);
        double[] yTrue = new double[Ng];
        for (int i = 0; i < Ng; i++) yTrue[i] = ytl.get(i);

        // ---- 集成模型预测 ypn ----
        double[] ypn = S.bp_predict(gX);

        // ---- 精度评价(有真实值时) ----
        double R2n = Double.NaN, RMSEN = Double.NaN;
        boolean[] ibn = new boolean[Ng];
        int accn = 0;
        if (hasTrue) {
            R2n = r2(yTrue, ypn);
            RMSEN = rmse(yTrue, ypn);
            for (int i = 0; i < Ng; i++) {
                ibn[i] = Math.abs(yTrue[i] - ypn[i]) <= ACC_THRESH;
                if (ibn[i]) accn++;
            }
            double accnPct = 100.0 * accn / Ng;
            System.out.printf("%n--- 验证集精度 ---%n");
            System.out.printf("  R2=%.4f  RMSE=%.3f  准确率=%.1f%% (%d/%d)  [阈值<=%.1f]%n",
                    R2n, RMSEN, accnPct, accn, Ng, ACC_THRESH);
        }

        // ---- 导出预测结果 CSV ----
        StringBuilder sb = new StringBuilder();
        if (hasTrue) sb.append(String.join(",", featureNames)).append(",真实锈蚀度,预测锈蚀度,绝对误差\n");
        else sb.append(String.join(",", featureNames)).append(",预测锈蚀度\n");
        for (int i = 0; i < Ng; i++) {
            for (int k = 0; k < 7; k++) sb.append(gX[i][k]).append(',');
            if (hasTrue) {
                sb.append(String.format("%.4f,%.4f,%.4f%n", yTrue[i], ypn[i], Math.abs(yTrue[i] - ypn[i])));
            } else {
                sb.append(String.format("%.4f%n", ypn[i]));
            }
        }
        String baseName = Paths.get(filepath).getFileName().toString().replaceAll("\\.csv$", "");
        String outFile = "预测结果_" + baseName + ".csv";
        Files.write(Paths.get(outFile), sb.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("==> 预测结果已导出: " + outFile);
    }

    // ---------- 列查找 ----------
    private static int findExact(String[] head, String name) {
        for (int i = 0; i < head.length; i++) if (head[i].trim().equals(name)) return i;
        return -1;
    }
    private static int findContains(String[] head, String[] kw, String[] exclude) {
        for (int i = 0; i < head.length; i++) {
            String c = head[i].trim();
            boolean hit = false;
            for (String k : kw) if (c.contains(k)) { hit = true; break; }
            if (!hit) continue;
            boolean ex = false;
            for (String e : exclude) if (c.contains(e)) { ex = true; break; }
            if (!ex) return i;
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

    /** 支持双引号包裹(字段内可含逗号, "" 为转义引号)的单行 CSV 分词。 */
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

    private static double median(List<Double> a) {
        List<Double> t = new ArrayList<>(a);
        t.sort(Double::compare);
        int m = t.size() / 2;
        return t.size() % 2 == 1 ? t.get(m) : (t.get(m - 1) + t.get(m)) / 2;
    }
    private static double r2(double[] y, double[] yp) {
        double ym = 0; for (double v : y) ym += v; ym /= y.length;
        double ssRes = 0, ssTot = 0;
        for (int i = 0; i < y.length; i++) {
            ssRes += (y[i] - yp[i]) * (y[i] - yp[i]);
            ssTot += (y[i] - ym) * (y[i] - ym);
        }
        return ssTot == 0 ? Double.NaN : 1 - ssRes / ssTot;
    }
    private static double rmse(double[] y, double[] yp) {
        double s = 0;
        for (int i = 0; i < y.length; i++) s += (y[i] - yp[i]) * (y[i] - yp[i]);
        return Math.sqrt(s / y.length);
    }
}
