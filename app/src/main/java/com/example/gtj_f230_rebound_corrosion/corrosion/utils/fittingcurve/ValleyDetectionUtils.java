package com.example.gtj_f230_rebound_corrosion.corrosion.utils.fittingcurve;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



public class ValleyDetectionUtils {
    private static StringBuilder stringBuilder = new StringBuilder();

    // 突出度门槛：峰突出度至少达到最大突出度的该比例，过滤基线毛刺和次级峰
    private static final double MIN_PROMINENCE_RATIO = 0.06;
    // 峰间最小间距（采样点数），过滤同一大包络内的震荡尖点
    private static final int MIN_PEAK_DISTANCE =30;
    // 最多保留的候选峰数
    private static final int TOP_N = 10;

    // 综合相对突出度、峰间最小间距筛选出最多 TOP_N 个候选峰，返回索引升序列表
    // 对应 Python: find_top_n_peaks
    public static List<Integer> findTopNPeaks(double[] data) {
        stringBuilder.delete(0, stringBuilder.length());
        // 1. 找到所有局部波峰（支持平顶峰）
        List<Integer> allPeaks = findAllPeaks(data);
        if (allPeaks.isEmpty()) {
            return new ArrayList<>();
        }
        // 2. 计算每个波峰的突出度，并记录最大突出度
        List<PeakInfo> peakInfos = new ArrayList<>();
        double maxProminence = 0;
        for (int idx : allPeaks) {
            double prominence = calcProminence(data, idx);
            if (prominence > maxProminence) {
                maxProminence = prominence;
            }
            peakInfos.add(new PeakInfo(idx, data[idx], prominence));
        }
        // 3. 相对突出度过滤：剔除低于最大突出度比例的毛刺峰和次级峰
        double thresholdProminence = maxProminence * MIN_PROMINENCE_RATIO;
        List<PeakInfo> validPeaks = new ArrayList<>();
        for (PeakInfo p : peakInfos) {
            if (p.prominence >= thresholdProminence) {
                validPeaks.add(p);
            }
        }
        // 4. 按突出度降序排序（突出度比原始幅值更能反映峰的真实显著性）
        validPeaks.sort((a, b) -> Double.compare(b.prominence, a.prominence));
        // 5. 贪心选取：与所有已选峰的间距都必须大于最小间距，最多保留 TOP_N 个
        List<Integer> selectedIndexes = new ArrayList<>();
        for (PeakInfo p : validPeaks) {
            boolean tooClose = false;
            for (int selected : selectedIndexes) {
                if (Math.abs(selected - p.index) <= MIN_PEAK_DISTANCE) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                selectedIndexes.add(p.index);
            }
            if (selectedIndexes.size() >= TOP_N) {
                break;
            }
        }
        // 6. 按索引位置升序返回（不在此处做窗口选型，窗口选型交由 findBest4PeakWindow）
        Collections.sort(selectedIndexes);
        stringBuilder.append("\n所有波峰：").append(allPeaks.size())
                .append(" ，有效波峰：").append(validPeaks.size())
                .append(" ，候选峰：").append(selectedIndexes);
        return selectedIndexes;
    }
    private static List<Integer> findAllPeaks(double[] data) {
//        List<Integer> peaks = new ArrayList<>();
//        for (int i = 1; i < data.length - 1; i++) {
//            if (data[i] > data[i - 1] && data[i] > data[i + 1]) {
//                peaks.add(i);
//            }
//        }
//        return peaks;

        List<Integer> peaks = new ArrayList<>();
        int length = data.length;
        if (length < 3) {
            return peaks;
        }

        int i = 1;
        while (i < length - 1) {
            // 寻找连续相等值的右边界（处理平顶峰）
            int j = i;
            while (j < length - 2 && data[j] == data[j + 1]) {
                j++;
            }

            // 判断该段是否为局部极大值
            if (data[i] > data[i - 1] && data[j] > data[j + 1]) {
                // 取平顶的中点作为峰值索引
                peaks.add((i + j) / 2);
            }

            // 跳到下一段
            i = j + 1;
        }

        return peaks;
   }

    // 计算波峰突出度：峰顶相对参考等高线的垂直高度
    // 参考等高线：从峰顶分别向左右两侧走到更高峰（或边界）为止，记录沿途最低点，取两侧最低点中的较高者
    private static double calcProminence(double[] data, int peakIndex) {
        double peakValue = data[peakIndex];
        // 向左搜索：遇到更高峰或到达左边界为止，记录沿途最低点
        double leftMin = peakValue;
        boolean hasHigherLeft = false;
        for (int i = peakIndex - 1; i >= 0; i--) {
            if (data[i] < leftMin) {
                leftMin = data[i];
            }
            if (data[i] > peakValue) {
                hasHigherLeft = true;
                break;
            }
        }
        // 向右搜索：同理
        double rightMin = peakValue;
        boolean hasHigherRight = false;
        for (int i = peakIndex + 1; i < data.length; i++) {
            if (data[i] < rightMin) {
                rightMin = data[i];
            }
            if (data[i] > peakValue) {
                hasHigherRight = true;
                break;
            }
        }
        double reference;
        if (hasHigherLeft || hasHigherRight) {
            reference = Math.max(leftMin, rightMin);
        } else {
            // 全局最高峰：以全曲线最小值作为参考
            reference = peakValue;
            for (double v : data) {
                if (v < reference) {
                    reference = v;
                }
            }
        }
        return peakValue - reference;
    }

    // 在两个峰索引之间查找最低点（波谷）——对应 Python: find_valley_between
    private static int findValleyBetween(double[] data, int peakIdxA, int peakIdxB) {
        int left = Math.min(peakIdxA, peakIdxB);
        int right = Math.max(peakIdxA, peakIdxB);
        if (right - left <= 1) {
            return left;
        }
        int minIdx = left + 1;
        double minVal = data[left + 1];
        for (int i = left + 1; i < right; i++) {
            if (data[i] < minVal) {
                minVal = data[i];
                minIdx = i;
            }
        }
        return minIdx;
    }

    // 在候选峰序列上滑动 4 峰窗口，组内相邻峰间找波谷，
    // 选取「中心波谷（第 2 个波谷，即 V4）最低」的一组——对应 Python: find_best_4peak_window
    private static FourPeakWindow findBest4PeakWindow(double[] data, List<Integer> peakIndices) {
        Double bestVal = null;
        List<Integer> bestPeaks = null;
        List<Integer> bestValleys = null;
        for (int i = 0; i + 3 < peakIndices.size(); i++) {
            List<Integer> window = peakIndices.subList(i, i + 4);
            int v1 = findValleyBetween(data, window.get(0), window.get(1));
            int v2 = findValleyBetween(data, window.get(1), window.get(2));
            int v3 = findValleyBetween(data, window.get(2), window.get(3));
            if (bestVal == null || data[v2] < bestVal) {
                bestVal = data[v2];
                bestPeaks = new ArrayList<>(window);
                bestValleys = new ArrayList<>(Arrays.asList(v1, v2, v3));
            }
        }
        if (bestPeaks == null) {
            return null;
        }
        return new FourPeakWindow(bestPeaks, bestValleys);
    }

    // 主算法：从波形数据中定位 4 峰 3 谷，供上层映射 7 个测点（V1..V7）
    // 统一策略（V4 恒落在波谷）：
    //   1) findTopNPeaks 挑选候选峰（相对突出度 + 峰间最小间距）；
    //   2) 候选峰数 >= 4：滑动 4 峰窗口取中心波谷最低的一组，
    //      上层映射 V1=谷1, V2=峰2, V4=谷2, V6=峰3, V7=谷3，
    //      V3=V2~V4 索引中点，V5=V4~V6 索引中点；
    //   3) 候选峰数 < 4 → 返回空 Map。
    // 对应 Python: find_seven_points
    public static Map<String, List<Integer>> findPeaksAndValleys(double[] data) {
        Map<String, List<Integer>> result = new HashMap<>();
        if (data == null || data.length < 3) {
            return result;
        }
        // 1. 候选峰挑选
        List<Integer> peakIndices = findTopNPeaks(data);
        if (peakIndices.size() < 4) {
            return result;
        }
        // 2. 滑动 4 峰窗口，取中心波谷最低的一组
        FourPeakWindow window = findBest4PeakWindow(data, peakIndices);
        if (window == null) {
            return result;
        }
        // peaks: 选中的 4 个波峰（升序）；valleys: 对应的 3 个波谷（V1/V4/V7，升序）
        result.put("peaks", window.peaks);
        result.put("valleys", window.valleys);
        return result;
    }

    // 辅助类：存储波峰信息
    static class PeakInfo {
        int index;
        double value;
        double prominence;

        PeakInfo(int index, double value, double prominence) {
            this.index = index;
            this.value = value;
            this.prominence = prominence;
        }
    }

    // 辅助类：滑动 4 峰窗口选中结果（4 峰 + 对应 3 谷）
    static class FourPeakWindow {
        List<Integer> peaks;
        List<Integer> valleys;

        FourPeakWindow(List<Integer> peaks, List<Integer> valleys) {
            this.peaks = peaks;
            this.valleys = valleys;
        }
    }

//    // 测试主函数
//    public static void main(String[] args) {
//        double[] temp = {265, 69, 68, 67, 68, 67, 66, 67, 68, 67, 66, 67, 68, 67, 68, 69, 68, 67, 65, 66, 67, 66, 67, 68, 69, 70, 71, 72, 74, 73, 75, 76, 81, 86, 87, 89, 91, 92, 98, 107, 113, 117, 119, 121, 134, 146, 150, 156, 161, 167, 172, 174, 208, 248, 254, 258, 269, 281, 322, 365, 383, 392, 400, 418, 504, 593, 620, 635, 648, 678, 712, 745, 952, 1160, 1210, 1267, 1322, 1381, 1673, 1970, 2042, 2112, 2183, 2262, 2537, 2814, 2895, 2934, 2977, 3062, 3480, 3884, 3945, 4009, 4071, 4130, 4315, 4487, 4510, 4526, 4516, 4486, 4458, 4427, 4392, 4354, 4076, 3789, 3724, 3650, 3574, 3497, 3101, 2704, 2630, 2594, 2554, 2473, 2209, 1951, 1880, 1810, 1743, 1676, 1398, 1130, 1085, 1041, 998, 957, 806, 664, 639, 617, 596, 574, 523, 476, 460, 449, 440, 430, 410, 397, 400, 405, 448, 496, 513, 530, 548, 567, 682, 809, 850, 888, 926, 968, 1136, 1310, 1365, 1422, 1484, 1548, 1561, 1615, 1684, 1935, 2188, 2265, 2693, 3116, 3227, 3307, 3341, 3616, 3878, 3911, 3942, 3972, 3996, 3994, 3973, 3948, 3916, 3793, 3665, 3616, 3560, 3503, 3445, 3080, 2716, 2651, 2578, 2507, 2472, 2172, 1840, 1777, 1722, 1666, 1606, 1445, 1291, 1243, 1195, 1152, 1108, 926, 749, 721, 697, 671, 646, 557, 472, 456, 443, 434, 424, 388, 358, 356, 353, 359, 368, 373, 380, 391, 400, 409, 421, 470, 521, 540, 560, 582, 603, 729, 860, 895, 935, 975, 1018, 1178, 1342, 1394, 1448, 1504, 1561, 1566, 1619, 1679, 1899, 2123, 2189, 2256, 2325, 2388, 2695, 3002, 3058, 3112, 3164, 3190, 3333, 3490, 3513, 3536, 3558, 3575, 3589, 3585, 3565, 3544, 3520, 3507, 3494, 3292, 3080, 3029, 2972, 2913, 2855, 2610, 2394, 2360, 2300, 2242, 2178, 2114, 2051, 1909, 1699, 1572, 1518, 1464, 1438, 1416, 1189, 967, 932, 898, 863, 845, 742, 625, 601, 581, 561, 538, 527, 517, 456, 400, 392, 382, 371, 366, 345, 326, 325, 327, 328, 342, 360, 367, 377, 387, 390, 396, 407, 496, 581, 592, 615, 638, 662, 769, 881, 920, 959, 980, 1002, 1194, 1394, 1453, 1513, 1578, 1641, 1704, 1765, 1802, 2169, 2572, 2665, 2758, 2844, 2933, 3219, 3499, 3586, 3681, 3779, 3871, 4202, 4525, 4598, 4667, 4727, 4784, 4842, 4877, 4903, 5050, 5186, 5204, 5219, 5170, 5097, 5063, 5026, 4984, 4939, 4716, 4485, 4418, 4353, 4286, 4214, 4138, 4062, 3939, 3669, 3446, 3370, 3293, 3219, 3184, 2851, 2486, 2418, 2348, 2279, 2214, 2061, 1940, 1909, 1849, 1791, 1736, 1685, 1634, 1581, 1370, 1164, 1125, 1089, 1049, 1015, 862, 725, 712, 686, 661, 637, 552, 467, 455, 450, 438, 422, 405, 389, 337, 292, 287, 275, 266, 256, 220, 183, 175, 170, 164, 159, 140, 122, 117, 112, 109, 106, 103, 93, 84, 80, 76, 75, 74, 65, 55, 53, 51, 49, 46, 44, 40, 39, 37, 35, 34, 33, 32, 29, 25, 23, 22, 23, 22, 20, 19, 18, 17, 15, 14, 13, 12, 9, 8, 9, 7, 5, 6, 5, 4, 3, 4, 5, 4, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 1, 0, 1, 2, 1, 3, 4, 5, 6, 4, 5, 8, 11, 13, 15, 16, 17, 20, 23, 24, 25, 33, 40, 41, 43, 44, 45, 44, 43, 42, 41, 42, 43, 45, 49, 51, 52, 55, 57, 60, 64, 67, 69, 72, 73, 72, 71, 70, 69, 68, 69, 70, 71, 74, 78, 79, 81, 85, 89, 92, 93, 94, 96, 98, 100, 103, 114, 127, 131, 134, 138, 142, 153, 168, 174, 176, 180, 183, 191, 199, 203, 207, 210, 213, 214, 219, 226, 229, 230, 231};
//
//        System.out.println("=== 查找4个明显波峰和3个波谷 ===");
//
//        Map<String, List<Integer>> result = findPeaksAndValleys(temp);
//        if (result.isEmpty()) {
//            return;
//        }
//        List<Integer> peaks = result.get("peaks");
//        List<Integer> valleys = result.get("valleys");
//
//        System.out.println("\n4个最明显的波峰:");
//        for (int i = 0; i < peaks.size(); i++) {
//            int idx = peaks.get(i);
//            System.out.printf("\n波峰" + (i + 1) + ": 索引=" + idx + ", 值=" + temp[idx]);
//        }
//
//        System.out.println("\n对应的3个波谷:");
//        for (int i = 0; i < valleys.size(); i++) {
//            int idx = valleys.get(i);
//            System.out.printf("\n波谷" + (i + 1) + ": 索引=" + idx + ", 值=" + temp[idx]);
//        }
//    }
}
