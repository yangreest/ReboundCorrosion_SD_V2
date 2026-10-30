package com.example.gtj_f230_rebound_corrosion.base.assessment;

import android.text.TextUtils;

import com.blankj.utilcode.util.SPUtils;
import com.example.gtj_f230_rebound_corrosion.R;
import com.example.gtj_f230_rebound_corrosion.base.model.ZoneBean;
import com.example.gtj_f230_rebound_corrosion.corrosion.model.CorrosionBean;
import com.example.gtj_f230_rebound_corrosion.f230.model.WidthBean;
import com.example.gtj_f230_rebound_corrosion.rebound.model.RebounderBean;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 混凝土电杆综合评估引擎。
 * 分级与评估规则移植自《混凝土电杆综合评估Demo.html》：
 * 裂缝：未检出＝I 级；≤0.10mm＝II 级；≤0.20mm＝III 级；＞0.20mm＝IV 级；
 * 强度：实测强度低于设计强度＝IV 级，否则＝I 级（需已录入设计强度）；
 * 锈蚀：＜5%＝I 级；＜15%＝II 级；＜30%＝III 级；≥30%＝IV 级；
 * 综合等级＝三项最高风险级，三项齐全且均可分级时形成。
 */
public class AssessmentEngine {

    public static final String[] ROMAN = {"—", "I", "II", "III", "IV"};
    public static final String[] LABELS = {"暂无等级", "未触发阈值", "关注", "异常", "严重"};
    private static final String[] METRIC_NAMES = {"最大受力裂缝宽度", "混凝土强度", "钢筋锈蚀率"};
    private static final String[] METRIC_UNITS = {"mm", "MPa", "%"};
    private static final String[] METRIC_SHORTS = {"裂缝", "强度", "锈蚀"};

    private static final String SP_DESIGN_STRENGTH = "assessment-design-strength-";
    private static final String SP_SNAPSHOTS = "assessment-snapshot-list";

    /**
     * 单项指标评估结果
     */
    public static class MetricEval {
        public int index;
        public String name;
        public String unit;
        public String shortName;
        public boolean hasData;
        public String valueText = "—";
        public Integer grade;
        public Integer knownGrade;
        public boolean complete;
        public String status = "数据不完整";
        //强度依据摘要（仅强度项）
        public String basisSummary;
        //明细警告tag文本
        public String detailTag;
        public int recordCount;
        public String timeText;
        public String ruleText;
    }

    /**
     * 单个测点（轮次内）综合评估结果
     */
    public static class PointEval {
        public List<MetricEval> metrics = new ArrayList<>();
        public int n;
        public boolean dataReady;
        public boolean complete;
        public Integer formalGrade;
        public Integer known;
        public List<String> controls = new ArrayList<>();
        public String state;
        public String adviceTitle;
        public String adviceDesc;
        public String processText;
        public boolean basisPending;
    }

    /**
     * 评估表格行（轮次/测点）
     */
    public static class RoundRow {
        public long zoneId;
        public String number;
        public int pointIndex;
        public String sub;
        public String crackText;
        public String strengthText;
        public String corrosionText;
        public int n;
        public boolean complete;
        public Integer formalGrade;
        public String state;
    }

    /**
     * 评估快照（保存本轮评估）
     */
    public static class Snapshot {
        public String id;
        public long zoneId;
        public int pointIndex;
        public String time;
        public String conclusion;
    }

    //============================= 数据解析 =============================

    public static List<WidthBean> widths(ZoneBean zone) {
        if (zone == null || zone.f230ZoneBean == null || TextUtils.isEmpty(zone.f230ZoneBean.strZoneList)) {
            return new ArrayList<>();
        }
        List<WidthBean> list = new Gson().fromJson(zone.f230ZoneBean.strZoneList, new TypeToken<List<WidthBean>>() {
        }.getType());
        return list == null ? new ArrayList<>() : list;
    }

    public static List<RebounderBean> rebounds(ZoneBean zone) {
        if (zone == null || zone.rebounderZoneBean == null || TextUtils.isEmpty(zone.rebounderZoneBean.strZoneList)) {
            return new ArrayList<>();
        }
        List<RebounderBean> list = new Gson().fromJson(zone.rebounderZoneBean.strZoneList, new TypeToken<List<RebounderBean>>() {
        }.getType());
        return list == null ? new ArrayList<>() : list;
    }

    public static List<CorrosionBean> corrosions(ZoneBean zone) {
        if (zone == null || zone.corrosionZoneBean == null || TextUtils.isEmpty(zone.corrosionZoneBean.strZoneList)) {
            return new ArrayList<>();
        }
        List<CorrosionBean> list = new Gson().fromJson(zone.corrosionZoneBean.strZoneList, new TypeToken<List<CorrosionBean>>() {
        }.getType());
        return list == null ? new ArrayList<>() : list;
    }

    /**
     * 构件测点数：三项检测测点数的最大值
     */
    public static int pointCount(ZoneBean zone) {
        return Math.max(widths(zone).size(), Math.max(rebounds(zone).size(), corrosions(zone).size()));
    }

    //============================= 设计强度 =============================

    public static String getDesignStrength(long projectId) {
        return SPUtils.getInstance().getString(SP_DESIGN_STRENGTH + projectId, "");
    }

    public static void setDesignStrength(long projectId, String value) {
        SPUtils.getInstance().put(SP_DESIGN_STRENGTH + projectId, value == null ? "" : value);
    }

    private static Double parsePositive(String text) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        try {
            double v = Double.parseDouble(text.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    //============================= 评估计算 =============================

    /**
     * 评估构件某个测点的三项检测结果与综合结论
     */
    public static PointEval evaluate(ZoneBean zone, int pointIndex, String designStrength) {
        PointEval eval = new PointEval();
        Double basis = parsePositive(designStrength);
        eval.basisPending = basis == null;

        List<WidthBean> widths = widths(zone);
        List<RebounderBean> rebounds = rebounds(zone);
        List<CorrosionBean> corrosions = corrosions(zone);

        //裂缝
        MetricEval crack = new MetricEval();
        crack.index = 0;
        crack.name = METRIC_NAMES[0];
        crack.unit = METRIC_UNITS[0];
        crack.shortName = METRIC_SHORTS[0];
        crack.ruleText = "分级规则：未检出＝I 级；≤0.10mm＝II 级；≤0.20mm＝III 级；＞0.20mm＝IV 级";
        WidthBean widthBean = pointIndex < widths.size() ? widths.get(pointIndex) : null;
        crack.hasData = widthBean != null;
        if (widthBean != null) {
            boolean detected = widthBean.width > 0;
            crack.valueText = detected ? fmt(widthBean.width) : "未检出";
            crack.grade = detected ? (widthBean.width <= 0.10 ? 2 : widthBean.width <= 0.20 ? 3 : 4) : 1;
            crack.knownGrade = crack.grade;
            crack.complete = true;
            crack.status = "有效";
            crack.recordCount = 1;
            crack.timeText = widthBean.time;
        }
        crack.detailTag = crack.complete ? null : crack.status;
        eval.metrics.add(crack);

        //强度
        MetricEval strength = new MetricEval();
        strength.index = 1;
        strength.name = METRIC_NAMES[1];
        strength.unit = METRIC_UNITS[1];
        strength.shortName = METRIC_SHORTS[1];
        strength.ruleText = "分级规则：实测强度低于设计强度＝IV 级，否则＝I 级";
        RebounderBean rebounderBean = pointIndex < rebounds.size() ? rebounds.get(pointIndex) : null;
        strength.hasData = rebounderBean != null;
        if (rebounderBean != null) {
            strength.valueText = TextUtils.isEmpty(rebounderBean.strCar) ? fmt(rebounderBean.carbonize) : rebounderBean.strCar;
            strength.recordCount = 1;
            if (basis != null) {
                strength.grade = rebounderBean.carbonize < basis ? 4 : 1;
                strength.knownGrade = strength.grade;
                strength.complete = true;
                strength.status = "有效";
            } else {
                strength.status = "待补充设计强度";
            }
            strength.basisSummary = basis == null ? "待补充设计强度"
                    : "q " + qText(rebounderBean.carbonize, basis) + "%" + (strength.grade != null ? " · " + ROMAN[strength.grade] + "级" : "");
        } else {
            strength.basisSummary = basis == null ? "待补充设计强度" : "—";
        }
        strength.detailTag = strength.complete ? null : strength.status;
        eval.metrics.add(strength);

        //锈蚀
        MetricEval corrosion = new MetricEval();
        corrosion.index = 2;
        corrosion.name = METRIC_NAMES[2];
        corrosion.unit = METRIC_UNITS[2];
        corrosion.shortName = METRIC_SHORTS[2];
        corrosion.ruleText = "分级规则：＜5%＝I 级；＜15%＝II 级；＜30%＝III 级；≥30%＝IV 级";
        CorrosionBean corrosionBean = pointIndex < corrosions.size() ? corrosions.get(pointIndex) : null;
        Double corrosionValue = corrosionBean == null ? null : parsePositive(corrosionBean.corrosion1);
        corrosion.hasData = corrosionValue != null && corrosionValue <= 100;
        if (corrosion.hasData) {
            corrosion.valueText = corrosionBean.corrosion1;
            corrosion.grade = corrosionValue < 5 ? 1 : corrosionValue < 15 ? 2 : corrosionValue < 30 ? 3 : 4;
            corrosion.knownGrade = corrosion.grade;
            corrosion.complete = true;
            corrosion.status = "有效";
            corrosion.recordCount = 1;
        }
        corrosion.detailTag = corrosion.complete ? null : corrosion.status;
        eval.metrics.add(corrosion);

        //汇总
        for (MetricEval metric : eval.metrics) {
            if (metric.hasData) {
                eval.n++;
            }
        }
        eval.dataReady = eval.n == 3;
        eval.complete = eval.dataReady && crack.grade != null && strength.grade != null && corrosion.grade != null;
        if (eval.complete) {
            eval.formalGrade = Math.max(crack.grade, Math.max(strength.grade, corrosion.grade));
        }
        List<Integer> knownGrades = new ArrayList<>();
        for (MetricEval metric : eval.metrics) {
            if (metric.knownGrade != null) {
                knownGrades.add(metric.knownGrade);
            }
        }
        if (!knownGrades.isEmpty()) {
            eval.known = knownGrades.get(0);
            for (Integer g : knownGrades) {
                eval.known = Math.max(eval.known, g);
            }
            for (MetricEval metric : eval.metrics) {
                if (metric.knownGrade != null && metric.knownGrade.equals(eval.known)) {
                    eval.controls.add(metric.shortName);
                }
            }
        }
        if (eval.complete) {
            eval.state = "已完成";
        } else if (eval.n == 0) {
            eval.state = "尚未形成综合等级";
        } else if (eval.dataReady && eval.basisPending) {
            eval.state = "待补充设计强度";
        } else {
            eval.state = "评估未完成";
        }

        //处置建议
        int knownVal = eval.known == null ? 0 : eval.known;
        if (knownVal >= 4) {
            eval.adviceTitle = "立即开展风险核查";
            eval.adviceDesc = "立即上报并核查控制点，具体处置由运维负责人确认。";
        } else if (knownVal == 3) {
            eval.adviceTitle = "尽快组织专项核查";
            eval.adviceDesc = "复测控制点，核实异常来源，按既有运维流程处理。";
        } else if (knownVal == 2) {
            eval.adviceTitle = "安排现场复核";
            eval.adviceDesc = "核对原始记录，确认后维护或修复，并安排复测。";
        } else if (eval.complete) {
            eval.adviceTitle = "按既有计划跟踪";
            eval.adviceDesc = "三项未触发分级阈值；外观异常仍需独立核查。";
        } else if (eval.dataReady && eval.basisPending) {
            eval.adviceTitle = "待补充设计强度";
            eval.adviceDesc = "本轮三项采集已完成，请在构件基本信息中录入设计强度（MPa）。";
        } else {
            eval.adviceTitle = "请补齐或复核数据";
            eval.adviceDesc = "评估尚未完成，请处理缺项、关联或复核问题。";
        }

        //评估过程
        StringBuilder process = new StringBuilder();
        if (eval.complete) {
            StringBuilder grades = new StringBuilder();
            for (MetricEval metric : eval.metrics) {
                if (grades.length() > 0) {
                    grades.append("，");
                }
                grades.append(ROMAN[metric.grade]);
            }
            process.append("取本点三项最高风险级：max（").append(grades).append("）＝ ").append(ROMAN[eval.formalGrade]).append(" 级");
        } else {
            process.append(eval.dataReady ? "三项采集已完成；请处理设计强度缺失或待复核事项。" : "三项数据尚未齐全，请补齐本轮缺项。");
        }
        process.append("已知单项风险继续展示。");
        process.append("\n");
        if (basis != null && rebounderBean != null) {
            process.append("设计强度 ").append(fmt(basis)).append(" MPa · ").append(strength.basisSummary);
        } else {
            process.append("强度比 q：待补充设计强度");
        }
        eval.processText = process.toString();
        return eval;
    }

    /**
     * 生成评估表格行：每个构件（轮次）的每个测点一行
     */
    public static List<RoundRow> buildRows(List<ZoneBean> zones, String designStrength) {
        List<RoundRow> rows = new ArrayList<>();
        if (zones == null) {
            return rows;
        }
        for (ZoneBean zone : zones) {
            if (zone == null || zone.id == null) {
                continue;
            }
            List<CorrosionBean> corrosions = corrosions(zone);
            int count = pointCount(zone);
            for (int i = 0; i < count; i++) {
                PointEval eval = evaluate(zone, i, designStrength);
                RoundRow row = new RoundRow();
                row.zoneId = zone.id;
                row.number = zone.number;
                row.pointIndex = i;
                String distance = i < corrosions.size() ? corrosions.get(i).distance : null;
                row.sub = !TextUtils.isEmpty(distance) ? distance + " m · 距根" : "测点 " + (i + 1) + " / " + count;
                row.crackText = eval.metrics.get(0).valueText;
                row.strengthText = eval.metrics.get(1).valueText;
                row.corrosionText = eval.metrics.get(2).valueText;
                row.n = eval.n;
                row.complete = eval.complete;
                row.formalGrade = eval.formalGrade;
                row.state = eval.complete ? ROMAN[eval.formalGrade] + "级" : eval.state;
                rows.add(row);
            }
        }
        return rows;
    }

    //============================= 快照保存 =============================

    public static void saveSnapshot(long zoneId, int pointIndex, PointEval eval) {
        String json = SPUtils.getInstance().getString(SP_SNAPSHOTS, "");
        List<Snapshot> list = TextUtils.isEmpty(json)
                ? new ArrayList<>()
                : new Gson().fromJson(json, new TypeToken<List<Snapshot>>() {
        }.getType());
        if (list == null) {
            list = new ArrayList<>();
        }
        Snapshot snapshot = new Snapshot();
        snapshot.id = "S" + String.format(Locale.CHINA, "%03d", list.size() + 1);
        snapshot.zoneId = zoneId;
        snapshot.pointIndex = pointIndex;
        snapshot.time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
        snapshot.conclusion = eval.complete ? ROMAN[eval.formalGrade] + "级" : eval.state;
        list.add(snapshot);
        SPUtils.getInstance().put(SP_SNAPSHOTS, new Gson().toJson(list));
    }

    //============================= 显示工具 =============================

    /**
     * 等级对应文字颜色
     */
    public static int gradeColorRes(Integer grade) {
        if (grade == null) {
            return R.color.color999;
        }
        switch (grade) {
            case 2:
                return R.color.assess_yellow;
            case 3:
                return R.color.assess_orange;
            case 4:
                return R.color.assess_red;
            default:
                return R.color.theme_color;
        }
    }

    /**
     * 数值显示：去掉多余的 0
     */
    public static String fmt(double value) {
        return new BigDecimal(Double.toString(value)).stripTrailingZeros().toPlainString();
    }

    /**
     * 强度比 q＝实测强度/设计强度×100%
     */
    private static String qText(double value, double reference) {
        if (reference <= 0) {
            return "—";
        }
        BigDecimal q = BigDecimal.valueOf(value)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(reference), 1, RoundingMode.DOWN);
        return q.stripTrailingZeros().toPlainString();
    }
}
