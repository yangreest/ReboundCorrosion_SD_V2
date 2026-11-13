package rustnet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-K 集成 BP 模型。加载由 MATLAB .mat 导出的文本模型文件,
 * 对多个基模型预测取平均。对应 MATLAB fenxi.m 中的 bestModels 集成。
 */
public class EnsembleModel {

    public List<BpModel> bestModels = new ArrayList<>();
    public int kTop;
    public double ACC_THRESH;
    public String[] featureNames;

    /**
     * 从文本模型文件加载集成模型。
     * @param file bp_model.txt 路径
     */
    public static EnsembleModel load(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return load(in);
        }
    }

    /**
     * 从流加载集成模型(如 Android assets), 解析逻辑与 load(Path) 一致。
     * @param in 模型文本流(UTF-8), 由调用方负责关闭
     */
    public static EnsembleModel load(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        List<String> lines = new ArrayList<>();
        String rawLine;
        while ((rawLine = reader.readLine()) != null) lines.add(rawLine);
        EnsembleModel ens = new EnsembleModel();
        BpModel cur = null;
        int li = 0;                 // 当前层序号
        int rowsLeft = 0;           // 待读权重行数
        int cols = 0;
        boolean readingW = false;
        boolean readingB = false;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] t = line.split("\\s+");

            if (line.startsWith("kTop")) {
                ens.kTop = Integer.parseInt(t[1]);
            } else if (line.startsWith("accThresh")) {
                ens.ACC_THRESH = Double.parseDouble(t[1]);
            } else if (line.startsWith("nModels")) {
                // 数量已按块解析, 此处仅读取
            } else if (line.startsWith("feature")) {
                ens.featureNames = new String[t.length - 1];
                for (int i = 1; i < t.length; i++) ens.featureNames[i - 1] = t[i];
            } else if (line.startsWith("===")) {
                cur = new BpModel();
                ens.bestModels.add(cur);
            } else if (line.startsWith("Xm")) {
                cur.Xm = parseVec(t);
            } else if (line.startsWith("Xs")) {
                cur.Xs = parseVec(t);
            } else if (line.startsWith("ym")) {
                cur.ym = Double.parseDouble(t[1]);
            } else if (line.startsWith("ys")) {
                cur.ys = Double.parseDouble(t[1]);
            } else if (line.startsWith("nLayers")) {
                // 层数由 W/b 块数量推断
            } else if (line.startsWith("W")) {
                li = Integer.parseInt(t[1]);
                int out = Integer.parseInt(t[2]);
                cols = Integer.parseInt(t[3]);
                double[][] Wl = new double[out][cols];
                cur.W.add(Wl);
                rowsLeft = out;
                readingW = true;
                readingB = false;
            } else if (line.startsWith("b")) {
                int out = Integer.parseInt(t[2]);
                double[][] bl = new double[out][1];
                cur.b.add(bl);
                readingW = false;
                readingB = true;
            } else if (readingW) {
                double[] row = parseAll(t);
                double[][] Wlast = cur.W.get(cur.W.size() - 1);
                System.arraycopy(row, 0, Wlast[Wlast.length - rowsLeft], 0, cols);
                rowsLeft--;
                if (rowsLeft == 0) readingW = false;
            } else if (readingB) {
                double[] bv = parseAll(t);
                double[][] blast = cur.b.get(cur.b.size() - 1);
                for (int i = 0; i < blast.length; i++) blast[i][0] = bv[i];
                readingB = false;
            }
        }
        if (ens.kTop <= 0) ens.kTop = ens.bestModels.size();
        return ens;
    }

    private static double[] parseAll(String[] t) {
        double[] v = new double[t.length];
        for (int i = 0; i < t.length; i++) v[i] = Double.parseDouble(t[i]);
        return v;
    }
    private static double[] parseVec(String[] t) {
        double[] v = new double[t.length - 1];
        for (int i = 1; i < t.length; i++) v[i - 1] = Double.parseDouble(t[i]);
        return v;
    }

    /** 集成预测: 各基模型 bp_predict 结果取平均 (对应 MATLAB fenxi.m 集成预测循环)。 */
    public double[] bp_predict(double[][] X) {
        int n = X.length;
        double[] sum = new double[n];
        for (BpModel m : bestModels) {
            double[] yp = m.bp_predict(X);
            for (int i = 0; i < n; i++) sum[i] += yp[i];
        }
        int k = Math.min(kTop, bestModels.size());
        for (int i = 0; i < n; i++) sum[i] /= k;
        return sum;
    }
}
