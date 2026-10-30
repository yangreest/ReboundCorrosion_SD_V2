package com.example.gtj_f230_rebound_corrosion.base.activity.data;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.core.content.ContextCompat;

import com.blankj.utilcode.util.ToastUtils;
import com.example.gtj_f230_rebound_corrosion.R;
import com.example.gtj_f230_rebound_corrosion.base.activity.BaseActivity;
import com.example.gtj_f230_rebound_corrosion.base.assessment.AssessmentEngine;
import com.example.gtj_f230_rebound_corrosion.base.model.ZoneBean;
import com.example.gtj_f230_rebound_corrosion.base.storage.GreenDaoHelper;
import com.example.gtj_f230_rebound_corrosion.base.view.StyleAlertDialog;
import com.example.gtj_f230_rebound_corrosion.databinding.ActAssessmentBinding;
import com.example.gtj_f230_rebound_corrosion.databinding.ItemAssessMetricBinding;

/**
 * 综合评估：本轮测点的三项检测结果 + 本轮综合评估
 */
@SuppressLint("SetTextI18n")
public class AssessmentActivity extends BaseActivity<ActAssessmentBinding> {

    public static final String EXTRA_ZONE_ID = "ZoneId";
    public static final String EXTRA_POINT_INDEX = "PointIndex";

    private ZoneBean zoneBean;
    private int pointIndex;
    private int pointCount;
    private AssessmentEngine.PointEval eval;

    @Override
    protected ActAssessmentBinding getBinding() {
        return ActAssessmentBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initLabel() {
        initBatteryReceiver(binding.navigationBar);
        initDeviceStatusExecutor(binding.navigationBar);
        binding.navigationBar.setTitleText("综合评估");
    }

    @Override
    protected void initView() {
        zoneBean = resolveZoneBean();
        if (zoneBean == null) {
            ToastUtils.showShort("数据不存在或已被删除");
            finish();
            return;
        }
        pointCount = AssessmentEngine.pointCount(zoneBean);
        pointIndex = getIntent().getIntExtra(EXTRA_POINT_INDEX, 0);
        if (pointCount > 0) {
            pointIndex = Math.min(Math.max(pointIndex, 0), pointCount - 1);
        } else {
            pointIndex = 0;
        }
        binding.tvPointPrev.setOnClickListener(this::onViewClicked);
        binding.tvPointNext.setOnClickListener(this::onViewClicked);
        binding.btnSave.setOnClickListener(this::onViewClicked);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (zoneBean != null && zoneBean.id != null) {
            ZoneBean dbZoneBean = GreenDaoHelper.getDaoSession(this).getZoneBeanDao().load(zoneBean.id);
            if (dbZoneBean != null) {
                zoneBean = dbZoneBean;
            }
        }
        if (zoneBean != null) {
            pointCount = AssessmentEngine.pointCount(zoneBean);
            if (pointCount > 0) {
                pointIndex = Math.min(Math.max(pointIndex, 0), pointCount - 1);
            }
            refresh();
        }
    }

    public void onViewClicked(View view) {
        if (view.getId() == binding.tvPointPrev.getId()) {
            if (pointIndex > 0) {
                pointIndex--;
                refresh();
            }
        } else if (view.getId() == binding.tvPointNext.getId()) {
            if (pointIndex < pointCount - 1) {
                pointIndex++;
                refresh();
            }
        } else if (view.getId() == binding.btnSave.getId()) {
            AssessmentEngine.saveSnapshot(zoneBean.id, pointIndex, eval);
            ToastUtils.showShort("本轮评估已保存");
        }
    }

    @SuppressLint("SetTextI18n")
    private void refresh() {
        String designStrength = AssessmentEngine.getDesignStrength(zoneBean.projectId);
        eval = AssessmentEngine.evaluate(zoneBean, pointIndex, designStrength);

        //头部
        binding.tvHeadTitle.setText(pointCount == 0
                ? "构件" + zoneBean.number + " / 暂无测点"
                : "构件" + zoneBean.number + " / 测点" + (pointIndex + 1));
        binding.tvPointIndex.setText((pointCount == 0 ? 0 : pointIndex + 1) + "/" + pointCount);
        float alpha = pointCount > 1 ? 1f : 0.3f;
        binding.tvPointPrev.setAlpha(alpha);
        binding.tvPointNext.setAlpha(alpha);
        binding.tvPill.setText(eval.n + "/3 项");
        binding.tvPill.setBackgroundResource(eval.dataReady ? R.drawable.bg_assess_pill : R.drawable.bg_assess_pill_pending);
        binding.tvPill.setTextColor(ContextCompat.getColor(this, eval.dataReady ? R.color.theme_color : R.color.assess_orange));

        //三项指标
        bindMetric(binding.metricCrack, eval.metrics.get(0), 1);
        bindMetric(binding.metricStrength, eval.metrics.get(1), 2);
        bindMetric(binding.metricCorrosion, eval.metrics.get(2), 3);

        //评估过程
        String process = "本轮评估过程\n" + eval.processText;
        SpannableString spannable = new SpannableString(process);
        spannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.theme_color)), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.tvProcess.setText(spannable);

        //右侧综合评估
        binding.tvSumLeft.setText("三项数据 " + eval.n + "/3");
        binding.tvSumPill.setText(eval.dataReady ? "本轮已完成" : "采集中");
        binding.tvSumPill.setBackgroundResource(eval.dataReady ? R.drawable.bg_assess_pill : R.drawable.bg_assess_pill_pending);
        binding.tvSumPill.setTextColor(ContextCompat.getColor(this, eval.dataReady ? R.color.theme_color : R.color.assess_orange));
        binding.flProgress.post(() -> {
            int width = binding.flProgress.getWidth();
            ViewGroup.LayoutParams layoutParams = binding.vProgressFill.getLayoutParams();
            layoutParams.width = (int) (width * eval.n / 3f);
            binding.vProgressFill.setLayoutParams(layoutParams);
        });
        if (eval.complete) {
            binding.tvBigGrade.setText(AssessmentEngine.ROMAN[eval.formalGrade] + " 级");
            binding.tvBigGrade.setTextColor(ContextCompat.getColor(this, AssessmentEngine.gradeColorRes(eval.formalGrade)));
        } else {
            binding.tvBigGrade.setText(eval.dataReady ? "待确认" : "未完成");
            binding.tvBigGrade.setTextColor(ContextCompat.getColor(this, R.color.color999));
        }
        binding.tvSumDesc.setText(eval.complete
                ? AssessmentEngine.LABELS[eval.formalGrade] + " · 当前测点结论"
                : eval.dataReady ? "三项数据已齐全 · " + eval.state : "当前测点尚有参数缺项");
        binding.tvSumDesc.setTextColor(ContextCompat.getColor(this, eval.complete
                ? AssessmentEngine.gradeColorRes(eval.formalGrade) : R.color.color666));

        binding.tvKnownTitle.setText(eval.complete ? "本轮控制指标" : "已知单项风险");
        binding.tvKnownGrade.setText(eval.known != null
                ? AssessmentEngine.ROMAN[eval.known] + " 级 · " + AssessmentEngine.LABELS[eval.known]
                : "暂无可判定单项");
        binding.tvKnownGrade.setTextColor(ContextCompat.getColor(this, AssessmentEngine.gradeColorRes(eval.known)));
        if (eval.controls.isEmpty()) {
            binding.tvKnownControls.setVisibility(View.GONE);
        } else {
            binding.tvKnownControls.setVisibility(View.VISIBLE);
            binding.tvKnownControls.setText("测点" + (pointIndex + 1) + " · " + TextUtils.join("、", eval.controls) + "控制");
        }
        binding.tvKnownTip.setVisibility(eval.complete ? View.GONE : View.VISIBLE);

        boolean urgent = eval.known != null && eval.known >= 4;
        binding.llAdvice.setBackgroundResource(urgent ? R.drawable.bg_assess_advice_urgent : R.drawable.bg_assess_advice);
        binding.tvAdviceTitle.setText(eval.adviceTitle);
        binding.tvAdviceDesc.setText(eval.adviceDesc);
        binding.tvSaveNote.setText("仅保存 构件" + zoneBean.number + " / 测点" + (pointIndex + 1) + " · 历史记录不覆盖");
    }

    @SuppressLint("SetTextI18n")
    private void bindMetric(ItemAssessMetricBinding metricBinding, AssessmentEngine.MetricEval metric, int number) {
        metricBinding.tvNumber.setText(number + "");
        metricBinding.tvTitle.setText(metric.name);
        boolean empty = TextUtils.equals("—", metric.valueText) || TextUtils.equals("未检出", metric.valueText);
        metricBinding.tvValue.setText(metric.valueText);
        metricBinding.tvValue.setTextColor(ContextCompat.getColor(this, empty ? R.color.color999 : R.color.theme_color));
        metricBinding.tvValue.setTextSize(TypedValue.COMPLEX_UNIT_DIP, empty ? 24 : 31);
        metricBinding.tvUnit.setVisibility(empty ? View.GONE : View.VISIBLE);
        metricBinding.tvUnit.setText(metric.unit);
        //明细：强度依据摘要 + 警告tag + 来源记录数
        if (metric.index == 1 && metric.basisSummary != null) {
            metricBinding.tvDetailText.setVisibility(View.VISIBLE);
            metricBinding.tvDetailText.setText(metric.basisSummary);
        } else {
            metricBinding.tvDetailText.setVisibility(View.GONE);
        }
        if (metric.detailTag != null) {
            metricBinding.tvDetailTag.setVisibility(View.VISIBLE);
            metricBinding.tvDetailTag.setText(metric.detailTag);
        } else {
            metricBinding.tvDetailTag.setVisibility(View.GONE);
        }
        if (metric.complete) {
            metricBinding.tvDetailCount.setVisibility(View.VISIBLE);
            metricBinding.tvDetailCount.setText(metric.recordCount + " 条来源记录");
        } else {
            metricBinding.tvDetailCount.setVisibility(View.GONE);
        }
        //待补充设计强度时，点击强度项明细可录入
        boolean basisInput = metric.index == 1 && eval.basisPending;
        metricBinding.tvDetailText.setOnClickListener(basisInput ? v -> showInputDialog() : null);
        metricBinding.tvDetailTag.setOnClickListener(basisInput ? v -> showInputDialog() : null);
        //等级与依据
        metricBinding.tvGrade.setText(metric.knownGrade != null
                ? AssessmentEngine.ROMAN[metric.knownGrade] + " 级" : "—");
        metricBinding.tvGrade.setTextColor(ContextCompat.getColor(this, AssessmentEngine.gradeColorRes(metric.knownGrade)));
        metricBinding.tvBasis.setOnClickListener(v -> showBasisDialog(metric));
    }

    /**
     * 查看依据
     */
    private void showBasisDialog(AssessmentEngine.MetricEval metric) {
        boolean empty = TextUtils.equals("—", metric.valueText) || TextUtils.equals("未检出", metric.valueText);
        StringBuilder content = new StringBuilder(metric.name);
        content.append("\n检测值：").append(metric.valueText).append(empty ? "" : " " + metric.unit);
        content.append("\n检测时间：").append(TextUtils.isEmpty(metric.timeText) ? "—" : metric.timeText);
        content.append("\n").append(metric.ruleText);
        content.append("\n当前等级：").append(metric.knownGrade != null
                ? AssessmentEngine.ROMAN[metric.knownGrade] + " 级（" + AssessmentEngine.LABELS[metric.knownGrade] + "）"
                : "暂无等级");
        if (metric.index == 1 && eval.basisPending) {
            content.append("\n设计强度：未录入，点击强度项“待补充设计强度”可录入");
        }
        StyleAlertDialog dialog = new StyleAlertDialog(this);
        dialog.setContent(content).setOKButton("关闭", v -> dialog.dismiss()).show();
    }

    /**
     * 录入设计强度
     */
    private void showInputDialog() {
        Dialog dialog = new Dialog(this, R.style.StyleAlertDialog);
        dialog.setContentView(R.layout.view_dialog_input);
        EditText editText = dialog.findViewById(R.id.et_input);
        editText.setText(AssessmentEngine.getDesignStrength(zoneBean.projectId));
        dialog.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btn_ok).setOnClickListener(v -> {
            String value = editText.getText().toString().trim();
            if (TextUtils.isEmpty(value)) {
                ToastUtils.showShort("请输入设计强度");
                return;
            }
            try {
                double strength = Double.parseDouble(value);
                if (strength <= 0) {
                    ToastUtils.showShort("设计强度需大于 0");
                    return;
                }
            } catch (NumberFormatException e) {
                ToastUtils.showShort("请输入数字");
                return;
            }
            AssessmentEngine.setDesignStrength(zoneBean.projectId, value);
            dialog.dismiss();
            ToastUtils.showShort("设计强度已保存");
            refresh();
        });
        dialog.show();
    }

    /**
     * 从 Intent 解析构件：优先按主键查库取最新数据，其次回退到序列化对象
     */
    private ZoneBean resolveZoneBean() {
        long zoneId = getIntent().getLongExtra(EXTRA_ZONE_ID, -1L);
        if (zoneId > 0) {
            ZoneBean dbZoneBean = GreenDaoHelper.getDaoSession(this).getZoneBeanDao().load(zoneId);
            if (dbZoneBean != null) {
                return dbZoneBean;
            }
        }
        return (ZoneBean) getIntent().getSerializableExtra("ZoneBean");
    }
}
